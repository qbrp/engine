package org.lain.engine.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.lain.engine.container.createContainer
import org.lain.engine.container.createSlotContainer
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemUuid
import org.lain.engine.server.*
import org.lain.engine.storage.*
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.util.Storage
import org.lain.engine.util.component.Component
import org.lain.engine.util.component.EntityCommandBuffer
import org.lain.engine.util.component.WriteComponentAccess
import org.lain.engine.util.component.require
import org.lain.engine.util.component.set
import org.lain.engine.util.component.setComponent
import org.lain.engine.util.component.setNullable
import org.lain.engine.util.math.Pos
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.lain.engine.world.location
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.apply

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
)

data class DefaultPlayerAttributes(
    var movement: MovementDefaultAttributes = MovementDefaultAttributes(),
    var minVolume: Float = 0.2f,
    var maxVolume: Float = 1.3f,
    var baseVolume: Float = 5f,
    var gravity: Float = 0.087f,
    var tirednessMultiplier: Float = 1f,
) : Component

fun commonPlayerInstance(
    settings: PlayerInstantiateSettings,
    id: PlayerId
): EnginePlayer {
    return EnginePlayer(id).apply {
        set(Location(settings.world, settings.pos))
        set(Velocity())
        set(Orientation())
        set(PlayerModel(skinEyeY = settings.skinEyeY))
        set(OrientationTranslation(0f, 0f))
        set(PlayerInventory(settings.items.toMutableSet()))
        set(ArmStatus(false))
        set(PlayerInput(mutableSetOf(), setOf()))
        set(Narration(mutableListOf()))
        set(DeveloperMode(settings.developerModeStatus.enabled, settings.developerModeStatus.acoustic))
        set(Hearing())
        set(ScriptBindings())
        set(settings.displayName)
        set(settings.movementStatus)
        set(settings.spectating)
        set(settings.gameMaster)
        set(settings.attributes)
        set(Synchronizations<EnginePlayer>(mutableMapOf()))
            .also { it.initializeSynchronizers() }
    }
}

fun serverPlayerInstance(
    settings: PlayerInstantiateSettings,
    persistent: PersistentPlayerData? = null,
    defaults: DefaultPlayerAttributes,
    id: PlayerId,
): EnginePlayer {
    val voiceApparatus = persistent?.voiceApparatus ?: VoiceApparatus(inputVolume = defaults.playerBaseInputVolume)

    return commonPlayerInstance(settings, id).apply {
        set(ServerPlayerInputMeta(false))
        set(MessageQueue())
        set(voiceApparatus)
        setNullable(persistent?.voiceLoose)
        set(defaults)
        set(PlayerChatHeadsComponent(persistent?.chatHeads ?: true))
        set(PlayerNetworkState(false))
        require<PlayerAttributes>().gravity.default = defaults.gravity
        set(AcousticMessageQueue(LinkedList()))
    }
}

private fun Synchronizations<EnginePlayer>.initializeSynchronizers() {
    submit(PLAYER_ARM_STATUS_SYNCHRONIZER)
    submit(PLAYER_CUSTOM_NAME_SYNCHRONIZER)
    submit(PLAYER_SPEED_INTENTION_SYNCHRONIZER)
    submit(PLAYER_NARRATION_SYNCHRONIZER)
    submit(PLAYER_ATTRIBUTES_SYNCHRONIZER)
    submit(PLAYER_EQUIPMENT_SYNCHRONIZER)
    submit(PLAYER_MODEL_SYNCHRONIZER)
    submit(PLAYER_HEARING_SYNCHRONIZER)
}

typealias PlayerStorage = Storage<PlayerId, EnginePlayer>

data class PlayerLoadSettings(
    val playerId: PlayerId,
    val inventoryItems: List<ItemUuid>,
    val notifications: List<Notification>,
    val initialPosition: Pos,
    val username: String,
    val developerModeStatus: DeveloperModeStatus,
    val world: WorldId
)

class PlayerLoader(
    private val server: EngineServer,
    private val itemLoader: ItemLoader,
) {
    private val commandBuffers = ConcurrentLinkedQueue<Pair<WorldId, EntityCommandBuffer>>()

    private suspend fun <R> ((Throwable) -> Unit).runCatchingSuspend(block: suspend () -> R): R? {
        return kotlin.runCatching { block() }
            .onFailure { this(it) }
            .getOrNull()
    }

    private fun ((Throwable) -> Unit).runCatching(block: () -> Unit) {
        kotlin.runCatching { block() }
            .onFailure { this(it) }
    }

    suspend fun loadPreparing(
        settings: PlayerLoadSettings,
        exceptionHandler: (Throwable) -> Unit
    ) {
        val persistent = server.globals.savePath.playerData.parsePersistentPlayerData(settings.playerId)
        val world = server.getWorld(settings.world)
        val inventoryLoadResult = exceptionHandler.runCatchingSuspend { loadInventoryItems(world, settings.inventoryItems, persistent?.equipment ?: mapOf()) } ?: return
        val player = exceptionHandler.runCatchingSuspend { serverPlayerInstance(world, settings, inventoryLoadResult, persistent)  } ?: return
        with(EntityCommandBuffer(world)) {
            player.prepareContainers(PersistentId.next(), player.location, inventoryLoadResult.equipmentItems)
            commandBuffers += world.id to this
        }
        server.execute {
            exceptionHandler.runCatching { server.instantiatePlayer(player, settings.notifications) }
        }
    }

    private fun serverPlayerInstance(
        world: World,
        settings: PlayerLoadSettings,
        inventoryItemsLoadResult: InventoryItemsLoadResult,
        persistentPlayerData: PersistentPlayerData?
    ): EnginePlayer {
        return serverPlayerInstance(
            PlayerInstantiateSettings(
                world,
                settings.initialPosition,
                DisplayName(
                    Username(settings.username),
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
                persistentPlayerData?.skinEyeY ?: 0f
            ),
            persistentPlayerData,
            server.globals.defaultPlayerAttributes,
            settings.playerId
        )
    }

    data class InventoryItemsLoadResult(val inventoryItems: List<EngineItem>, val equipmentItems: Map<EquipmentSlot, EngineItem>)

    private suspend fun loadInventoryItems(
        world: World,
        inventoryItems: List<ItemUuid>,
        equipmentItems: Map<EquipmentSlot, ItemUuid>
    ): InventoryItemsLoadResult = withContext(Dispatchers.IO) {
        val inventoryItems = async {
            val items = inventoryItems.map { uuid ->
                async { itemLoader.loadWorldItem(uuid, world) }
            }
            items.awaitAll().filterNotNull()
        }

        val equipment = async {
            equipmentItems
                .toList()
                .map { (slot, uuid) ->
                    async {
                        val item = itemLoader.loadWorldItem(uuid, world)
                        slot to item
                    }
                }
                .awaitAll()
                .filter { (_, item) -> item != null }
                .toMap() as Map<EquipmentSlot, EngineItem>
        }

        InventoryItemsLoadResult(inventoryItems.await(), equipment.await())
    }

    fun applyCommands(world: World) {
        commandBuffers.forEach { (worldId, buffer) ->
            if (world.id == worldId) buffer.apply(world)
        }
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
        persistentId = PersistentId("inventory-$playerUuid"),
    )
    void.setComponent(PlayerContainerTag)
    set(PlayerContainer(void))

    val container = componentAccess.createSlotContainer(
        location,
        EquipmentSlot.slotIds,
        networked = true,
        items = equipmentItems.mapKeys { (slot, _) -> slot.slotId },
        persistentId = persistentId
    )
    container.setComponent(PlayerEquipment(this@prepareContainers))
    set(Equipment(container))
}