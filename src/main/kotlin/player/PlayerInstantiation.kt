package org.lain.engine.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.container.createContainer
import org.lain.engine.container.createSlotContainer
import org.lain.engine.item.EngineItem
import org.lain.engine.mc.ReplayViewer
import org.lain.engine.mc.commands.friendlyError
import org.lain.engine.player.character.AppliedCharacters
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.character.applyCharacter
import org.lain.engine.player.character.prepareCharacter
import org.lain.engine.player.interaction.PlayerInput
import org.lain.engine.script.lua.LuaContext
import org.lain.engine.script.lua.prepareLuaScriptComponents
import org.lain.engine.server.*
import org.lain.engine.storage.*
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.util.Storage
import org.lain.engine.util.component.EntityCommandBuffer
import org.lain.engine.util.component.Networked
import org.lain.engine.util.math.Pos
import org.lain.engine.world.Location
import org.lain.engine.world.World
import java.util.*
import kotlin.apply

data class Player(val obj: EnginePlayer) : Component

data class PlayerInstantiateSettings(
    val world: World,
    val pos: Pos,
    val displayName: DisplayName,
    val movementStatus: MovementStatus = MovementStatus(),
    val attributes: PlayerAttributes = PlayerAttributes(),
    val spectating: Spectating = Spectating(),
    val gameMaster: GameMaster = GameMaster(),
    val developerModeStatus: DeveloperModeStatus,
    val items: Set<EngineItem> = setOf(),
    val skinEyeY: Float = 0f,
    val replayViewer: Boolean = false
)

data class DefaultPlayerAttributes(
    val movement: MovementDefaultAttributes = MovementDefaultAttributes(),
    val minVolume: Float = 0.2f,
    val maxVolume: Float = 1.3f,
    val baseVolume: Float = 5f,
    val gravity: Float = 0.087f,
    val flyingSpeed: Float = 1f,
    val tirednessMultiplier: Float = 1f,
) : Component

context(write: WriteComponentAccess)
fun commonPlayerInstance(
    settings: PlayerInstantiateSettings,
    id: PlayerId
): EnginePlayer {
    val entity =  settings.world.addEntity()
        .apply {
            setComponent(PersistentIdComponent(CustomPersistentId(id.toString())))
            setComponent(Location(settings.pos))
            setComponent(Velocity())
            setComponent(Orientation())
            setComponent(EnginePlayerModel(skinEyeY = settings.skinEyeY))
            setComponent(OrientationTranslation(0f, 0f))
            setComponent(PlayerInventory(settings.items.toMutableSet()))
            setComponent(ArmStatus(false))
            setComponent(Narration(mutableListOf()))
            setComponent(DeveloperMode(settings.developerModeStatus.enabled, settings.developerModeStatus.acoustic))
            setComponent(Hearing())
            setComponent(ScriptBindings())
            setComponent(PlayerInput())
            setComponent(settings.displayName)
            setComponent(settings.movementStatus)
            setComponent(settings.spectating)
            setComponent(settings.gameMaster)
            setComponent(settings.attributes)
            setComponent(
                Synchronizations<EnginePlayer>(mutableMapOf())
                    .also { it.initializeSynchronizers() }
            )
            if (settings.replayViewer) {
                setComponent(ReplayViewer)
            }
        }
    val player = EnginePlayer(id, entity, world = settings.world)
    entity.setComponent(Player(player))
    return player
}

context(write: WriteComponentAccess)
fun serverPlayerInstance(
    settings: PlayerInstantiateSettings,
    persistent: PersistentPlayerData? = null,
    defaults: DefaultPlayerAttributes,
    id: PlayerId,
): EnginePlayer {
    val voiceApparatus = persistent?.voiceApparatus ?: VoiceApparatus(inputVolume = defaults.playerBaseInputVolume)
    val player = commonPlayerInstance(settings, id)
    player.entity.apply {
        setComponent(MessageQueue())
        setComponent(voiceApparatus)
        persistent?.voiceLoose?.let { setComponent(it) }
        setComponent(defaults)
        setComponent(PlayerChatHeadsComponent(persistent?.chatHeads ?: true))
        setComponent(PlayerNetworkState(false))
        //require<PlayerAttributes>().gravity.default = defaults.gravity
        setComponent(AcousticMessageQueue(LinkedList()))
        setComponent(AppliedCharacters(mutableMapOf()))
        setComponent(Networked)
    }
    return player
}

private fun Synchronizations<EnginePlayer>.initializeSynchronizers() {
    submit(PLAYER_ARM_STATUS_SYNCHRONIZER)
    submit(PLAYER_CUSTOM_NAME_SYNCHRONIZER)
    submit(PLAYER_SPEED_INTENTION_SYNCHRONIZER)
    submit(PLAYER_NARRATION_SYNCHRONIZER)
    submit(PLAYER_ATTRIBUTES_SYNCHRONIZER)
    submit(PLAYER_MODEL_SYNCHRONIZER)
    submit(PLAYER_HEARING_SYNCHRONIZER)
}

typealias PlayerStorage = Storage<PlayerId, EnginePlayer>

data class PlayerLoadSettings(
    val playerId: PlayerId,
    val inventoryItems: List<PersistentId>,
    val notifications: List<Notification>,
    val initialPosition: Pos,
    val username: String,
    val developerModeStatus: DeveloperModeStatus,
    val world: World,
    val isReplayViewer: Boolean = false,
    val persistentPlayerData: PersistentPlayerData?
) {
    data class Account(val character: EngineCharacter?)
}

class PlayerLoader(
    private val server: EngineServer,
    private val itemLoader: ItemLoader,
) {
    suspend fun loadPreparing(
        settings: PlayerLoadSettings,
        account: PlayerLoadSettings.Account
    ) {
        if (server.playerStorage.get(settings.playerId) != null) {
            friendlyError("Игрок уже находится на сервере")
        }

        val world = settings.world
        val persistent = settings.persistentPlayerData
        val inventoryLoadResult = loadInventoryItems(
            world,
            settings.inventoryItems,
            persistent?.equipment ?: mapOf(),
            ItemLoadContext.PreparingPlayer(settings.playerId, settings.username)
        )
        val location = Location(settings.initialPosition)
        with(EntityCommandBuffer(world)) {
            val player = serverPlayerInstance(world, settings, inventoryLoadResult, persistent)
            val character = account.character
            val persistentCharacterData = persistent?.characters[character?.profile?.id]
            persistentCharacterData?.let { player.prepareCharacter(persistentCharacterData) }

            val componentsToLoad = persistent?.components.orEmpty()
            player.prepareContainers(Uuid.next(), location, inventoryLoadResult.equipmentItems)
            player.entity.copyComponentDtoState(componentsToLoad) {
                toDomainWithoutRelationships(
                    world.itemStorage,
                    server.namespacedStorage
                )
            }
            withContext(server.dispatcher) {
                server.itemLoader.apply(world)
                apply(world)
                server.instantiatePlayer(player, settings.notifications, character, persistentCharacterData)
                server.handler.onCharacterApplyConfirmation(player)
            }
        }
    }

    context(write: WriteComponentAccess)
    private fun serverPlayerInstance(
        world: World,
        settings: PlayerLoadSettings,
        inventoryItemsLoadResult: InventoryItemsLoadResult,
        persistentPlayerData: PersistentPlayerData?,
    ): EnginePlayer {
        return serverPlayerInstance(
            PlayerInstantiateSettings(
                world,
                settings.initialPosition,
                DisplayName(
                    Username(settings.username.filter { !it.isWhitespace() }),
                    persistentPlayerData?.customName?.toDomain(settings.username)
                ),
                MovementStatus(
                    intention = persistentPlayerData?.speedIntention ?: MovementStatus.DEFAULT_INTENTION,
                    stamina = persistentPlayerData?.stamina ?: MovementStatus.DEFAULT_STAMINA
                ),
                PlayerAttributes(),
                Spectating(),
                GameMaster(),
                settings.developerModeStatus,
                inventoryItemsLoadResult.inventoryItems.toSet(),
                persistentPlayerData?.skinEyeY ?: 0f,
                settings.isReplayViewer,
            ),
            persistentPlayerData,
            server.globals.defaultPlayerAttributes,
            settings.playerId,
        )
    }

    data class InventoryItemsLoadResult(
        val inventoryItems: List<EngineItem>,
        val equipmentItems: Map<EquipmentSlot, EngineItem>
    )

    private suspend fun loadInventoryItems(
        world: World,
        inventoryItems: List<PersistentId>,
        equipmentItems: Map<EquipmentSlot, PersistentId>,
        context: ItemLoadContext.PreparingPlayer
    ): InventoryItemsLoadResult = withContext(Dispatchers.IO) {
        val inventoryItems = async {
            val items = inventoryItems.map { uuid ->
                async { itemLoader.loadWorldItem(uuid, world, context) }
            }
            items.awaitAll().filterNotNull()
        }

        val equipment = async {
            equipmentItems
                .toList()
                .map { (slot, uuid) ->
                    async {
                        val item = itemLoader.loadWorldItem(uuid, world, context)
                        slot to item
                    }
                }
                .awaitAll()
                .toMap() as Map<EquipmentSlot, EngineItem>
        }

        InventoryItemsLoadResult(inventoryItems.await(), equipment.await())
    }
}


context(componentAccess: WriteComponentAccess)
fun EnginePlayer.prepareContainers(
    persistentId: PersistentId,
    location: Location,
    equipmentItems: Map<EquipmentSlot, EngineItem>
) {
    val playerUuid = this@prepareContainers.id
    val void = componentAccess.createContainer(
        location,
        networked = true,
        persistentId = persistentId("inventory-$playerUuid"),
    )
    void.setComponent(PlayerContainerTag)
    entity.setComponent(PlayerContainer(void))

    val container = componentAccess.createSlotContainer(
        location,
        EquipmentSlot.slotIds,
        networked = true,
        items = equipmentItems.mapKeys { (slot, _) -> slot.slotId },
        persistentId = persistentId
    )
    container.setComponent(PlayerEquipment(this@prepareContainers))
    entity.setComponent(Equipment(container))
}
