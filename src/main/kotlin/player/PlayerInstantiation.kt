package org.lain.engine.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.container.createContainer
import org.lain.engine.container.createSlotContainer
import org.lain.engine.item.EngineItem
import org.lain.engine.mc.commands.friendlyError
import org.lain.engine.player.character.UsedCharacters
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.interaction.PlayerInput
import org.lain.engine.server.*
import org.lain.engine.data.*
import org.lain.engine.player.character.Look
import org.lain.engine.player.character.getDisplay
import org.lain.engine.player.character.getPhysical
import org.lain.engine.player.character.setCharacterComponents
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.server.replication.Networked
import org.lain.engine.server.replication.Interests
import org.lain.engine.server.replication.PlayerInstantiationConfirmation
import org.lain.engine.server.replication.PlayerSyncState
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.math.Pos
import org.lain.engine.world.Location
import org.lain.engine.world.World
import java.util.*
import kotlin.apply

data class PlayerComponent(val obj: EnginePlayer) : Component

data class PlayerInstantiateSettings(
    val world: World,
    val pos: Pos,
    val displayName: DisplayName,
    val mode: PlayerModeComponent = PlayerModeComponent(PlayerMode.SPECTATOR),
    val movementStatus: MovementStatus = MovementStatus(),
    val attributes: PlayerAttributes = PlayerAttributes(),
    val developerModeStatus: DeveloperModeStatus,
    val items: Set<EngineItem> = setOf(),
    val skinEyeY: Float = 0f,
    val replayViewer: Boolean = false
)

data class DefaultPlayerAttributes(
    val minVolume: Float = 0.2f,
    val maxVolume: Float = 1.3f,
    val baseVolume: Float = 5f,
    val gravity: Float = 0.087f,
    val tirednessMultiplier: Float = 1f,
) : Component

context(write: WriteComponentAccess)
fun commonPlayerInstance(
    settings: PlayerInstantiateSettings,
    id: PlayerId,
    entity: EntityId = settings.world.addEntity(),
    character: EngineCharacter?,
    look: Look?
): EnginePlayer {
    entity
        .apply {
            setComponent(PersistentIdComponent(CustomPersistentId(id.toString())))
            setComponent(Location(settings.pos))
            setComponent(Velocity())
            setComponent(Orientation())
            setComponent(EnginePlayerModel(skinEyeY = settings.skinEyeY))
            setComponent(PlayerInventory(settings.items.toMutableSet()))
            setComponent(ArmStatus(false))
            setComponent(Narration(mutableListOf()))
            setComponent(DeveloperMode(settings.developerModeStatus.enabled, settings.developerModeStatus.acoustic))
            setComponent(Hearing())
            setComponent(ScriptBindings())
            setComponent(PlayerInput())
            setComponent(PlayerPhysics())
            setComponent(settings.displayName)
            setComponent(settings.movementStatus)
            setComponent(settings.mode)
            setComponent(settings.attributes)
            setComponent(CustomPlayerAttributes())
            character?.let {
                setCharacterComponents(
                    character.getPhysical(),
                    character.getDisplay(),
                    look ?: character.baseLook,
                    character
                )
            }
            if (settings.replayViewer) {
                setComponent(ReplayViewer)
            }
        }
    val player = EnginePlayer(id, entity, world = settings.world)
    entity.setComponent(PlayerComponent(player))
    return player
}

context(write: WriteComponentAccess)
fun serverPlayerInstance(
    settings: PlayerInstantiateSettings,
    persistent: PersistentPlayerData? = null,
    defaults: DefaultPlayerAttributes,
    id: PlayerId,
    entity: EntityId,
): EnginePlayer {
    val voiceApparatus = persistent?.voiceApparatus ?: VoiceApparatus(inputVolume = defaults.playerBaseInputVolume)
    val player = commonPlayerInstance(settings, id, entity, null, null)
    player.entity.apply {
        setComponent(MessageQueue())
        setComponent(voiceApparatus)
        persistent?.voiceLoose?.let { setComponent(it) }
        setComponent(defaults)
        setComponent(PlayerChatHeadsComponent(persistent?.chatHeads ?: true))
        setComponent(PlayerInstantiationConfirmation())
        setComponent(PlayerSyncState())
        setComponent(Interests())
        //require<PlayerAttributes>().gravity.default = defaults.gravity
        setComponent(AcousticMessageQueue(LinkedList()))
        setComponent(UsedCharacters(persistent?.usedCharacters.orEmpty().toMutableSet()))
        setComponent(Networked)
    }
    return player
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
    void.entity.setComponent(PlayerContainerTag)
    entity.setComponent(PlayerContainer(void.entity))

    val container = componentAccess.createSlotContainer(
        location,
        EquipmentSlot.slotIds,
        networked = true,
        items = equipmentItems.mapKeys { (slot, _) -> slot.slotId },
        persistentId = persistentId
    )
    container.entity.setComponent(PlayerEquipment(this@prepareContainers))
    entity.setComponent(Equipment(container))
}


data class PlayerLoadSettings(
    val playerId: PlayerId,
    val inventoryItems: List<PersistentId>,
    val notifications: List<Notification>,
    val initialPosition: Pos,
    val username: String,
    val developerModeStatus: DeveloperModeStatus,
    val world: World,
    val isReplayViewer: Boolean = false,
    val playerMode: PlayerMode,
) {
    data class Account(val character: EngineCharacter?) // название Account может слегка путать, ибо выбор не привязан к аккаунту
}

class PlayerLoader(
    private val server: EngineServer,
    private val itemLoader: ItemLoader,
) {
    private val persistence = server.playerPersistence

    suspend fun loadPreparing(
        settings: PlayerLoadSettings,
        account: PlayerLoadSettings.Account,
        onCreated: (EnginePlayer) -> Unit = {},
    ): EnginePlayer = withContext(Dispatchers.IO) {
        if (server.playerStorage.get(settings.playerId) != null) {
            friendlyError("Игрок уже находится на сервере")
        }

        val world = settings.world
        val playerId = settings.playerId
        val transaction = server.createTransactionContext(world)

        val prepared = try {
            val persistentRecord = persistence.loadRecord(playerId)
            val persistentData = persistentRecord?.data

            val playerEntity = persistentRecord?.let {
                transaction.withChild { child ->
                    persistence.loadPersistentEntity(
                        playerId,
                        child,
                    )
                }
            }

            val inventory = transaction.withChild { child ->
                loadInventoryItems(
                    child,
                    settings.inventoryItems,
                    persistentData?.equipment.orEmpty(),
                    ItemLoadContext.PreparingPlayer(
                        playerId,
                        settings.username,
                    ),
                )
            }

            val player = with(transaction.commands) {
                serverPlayerInstance(
                    world,
                    settings,
                    inventory,
                    persistentData,
                    playerEntity ?: world.addEntity(),
                ).also {
                    it.prepareContainers(
                        Uuid.next(),
                        Location(settings.initialPosition),
                        inventory.equipmentItems,
                    )
                }
            }

            val character = account.character
            val characterId = character?.profile?.id

            val persistentCharacter = if (
                characterId != null &&
                characterId in persistentData?.usedCharacters.orEmpty()
            ) {
                persistence.loadPersistentCharacter(
                    world.componentReviveSettings,
                    playerId,
                    characterId,
                )
            } else {
                null
            }

            PreparedPlayer(player, character, persistentCharacter)
        } catch (exception: Throwable) {
            transaction.rollback(exception)
            throw exception
        }

        withContext(server.dispatcher) {
            transaction.commit()

            server.instantiatePlayer(
                prepared.player,
                settings.notifications,
                prepared.character,
                prepared.persistentCharacter,
            )

            onCreated(prepared.player)

            prepared.player
        }
    }

    private data class PreparedPlayer(
        val player: EnginePlayer,
        val character: EngineCharacter?,
        val persistentCharacter: PersistentCharacterRecord?,
    )

    context(write: WriteComponentAccess)
    private fun serverPlayerInstance(
        world: World,
        settings: PlayerLoadSettings,
        inventoryItemsLoadResult: InventoryItemsLoadResult,
        persistentPlayerData: PersistentPlayerData?,
        entity: EntityId,
    ): EnginePlayer {
        return serverPlayerInstance(
            PlayerInstantiateSettings(
                world,
                settings.initialPosition,
                DisplayName(
                    Username(settings.username.filter { !it.isWhitespace() }),
                    persistentPlayerData?.customName?.toDomain(settings.username)
                ),
                PlayerModeComponent(settings.playerMode),
                MovementStatus(
                    intention = persistentPlayerData?.speedIntention ?: MovementStatus.DEFAULT_INTENTION,
                    stamina = persistentPlayerData?.stamina ?: MovementStatus.DEFAULT_STAMINA
                ),
                PlayerAttributes(),
                settings.developerModeStatus,
                inventoryItemsLoadResult.inventoryItems.toSet(),
                persistentPlayerData?.skinEyeY ?: 0f,
                settings.isReplayViewer,
            ),
            persistentPlayerData,
            server.globals.defaultPlayerAttributes,
            settings.playerId,
            entity
        )
    }

    data class InventoryItemsLoadResult(
        val inventoryItems: List<EngineItem>,
        val equipmentItems: Map<EquipmentSlot, EngineItem>
    )

    private suspend fun loadInventoryItems(
        transactionContext: TransactionContext,
        inventoryItems: List<PersistentId>,
        equipmentItems: Map<EquipmentSlot, PersistentId>,
        itemLoadContext: ItemLoadContext.PreparingPlayer
    ): InventoryItemsLoadResult = coroutineScope {
        val inventoryJobs = inventoryItems.map { uuid ->
            async(Dispatchers.IO) {
                transactionContext.withChild { childContext ->
                    itemLoader.loadWorldItem(
                        uuid,
                        itemLoadContext,
                        childContext,
                    )
                }
            }
        }

        val equipmentJobs = equipmentItems.map { (slot, uuid) ->
            async(Dispatchers.IO) {
                transactionContext.withChild { childContext ->
                    slot to itemLoader.loadWorldItem(
                        uuid,
                        itemLoadContext,
                        childContext,
                    )
                }
            }
        }

        val inventory = inventoryJobs.awaitAll()
        val equipment = equipmentJobs.awaitAll().toMap()
        transactionContext.awaitChildren()

        InventoryItemsLoadResult(inventory, equipment)
    }
}
