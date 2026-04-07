package org.lain.engine.server

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import org.lain.cyberia.ecs.*
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketCodec
import org.lain.engine.util.component.Entity
import org.lain.engine.util.math.filterNearestPlayers
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.Location
import org.lain.engine.world.location
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.reflect.KClass

data class PlayerNetworkState(
    var authorized: Boolean,
    val players: MutableList<EnginePlayer> = mutableListOf(),
    val items: MutableList<ItemUuid> = Collections.synchronizedList(mutableListOf()),
    val chunks: MutableList<EngineChunkPos> = mutableListOf(),
    var disconnect: Boolean = false,
    var tick: Long = 0,
) : Component

val EnginePlayer.network
    get() = this.require<PlayerNetworkState>()

// Common synchronizers

data class Synchronizations<T : Entity>(val state: MutableMap<KClass<out Component>, State<T>> = mutableMapOf()) : Component {
    data class State<T : Entity>(var dirty: DirtyState? = null, val synchronizer: ComponentSynchronizer<T, *>)
}

data class DirtyState(val interaction: InteractionId?)

inline fun <T : Entity, reified C : Component> Synchronizations<T>.submit(synchronizer: ComponentSynchronizer<T, C>) {
    state[C::class] = Synchronizations.State(null, synchronizer)
}

fun Entity.markDirty(componentClass: KClass<out Component>, interactionId: InteractionId? = null) {
    val state = require<Synchronizations<*>>().state[componentClass] ?: error("Component synchronizer for $componentClass not found")
    state.dirty = DirtyState(interactionId)
}

inline fun <reified C : Component> Entity.markDirty(interactionId: InteractionId? = null) {
    markDirty(C::class, interactionId)
}

enum class PlayerPredicate {
    ALL, SELF, OTHERS
}


enum class SynchronizationTarget {
    PLAYER, ITEM
}

enum class Propagation {
    DISTANCE, GLOBAL
}

class ComponentSynchronizer<T : Entity, C : Component> @OptIn(ExperimentalSerializationApi::class) constructor(
    val componentType: ComponentType<C>,
    val serializer: KSerializer<C>,
    val target: SynchronizationTarget,
    val propagation: Propagation,
    val resolver: (T, C) -> Unit,
    val predicate: PlayerPredicate,
    val endpoint: Endpoint<ComponentSynchronizationPacket<C>> = Endpoint(
        componentType.id,
        PacketCodec.Binary(
            {
                val id = readString()
                val interaction = readNullable { it.readLong() }
                ComponentSynchronizationPacket<C>(
                    id,
                    interaction?.let { InteractionId(it) },
                    ProtoBuf.decodeFromByteArray(serializer, readByteArray()),
                )
            },
            {
                writeString(it.id)
                writeNullable(it.interaction?.value) { buf, value -> buf.writeLong(value) }
                writeByteArray(ProtoBuf.encodeToByteArray(serializer, it.component))
            }
        )
    ),
)

@OptIn(InternalSerializationApi::class)
inline fun <T : Entity, reified C : Component> ComponentSynchronizer(
    target: SynchronizationTarget,
    propagation: Propagation,
    predicate: PlayerPredicate,
    noinline resolver: (T, C) -> Unit,
) = ComponentSynchronizer<T, C>(
    componentTypeOf(C::class),
    C::class.serializer(),
    target,
    propagation,
    resolver,
    predicate
)

inline fun <reified C : Component> PlayerComponentSynchronizer(
    predicate: PlayerPredicate,
    propagation: Propagation = Propagation.DISTANCE,
    noinline resolver: (EnginePlayer, C) -> Unit,
) = ComponentSynchronizer(
    SynchronizationTarget.PLAYER,
    propagation,
    predicate,
    resolver,
)

inline fun <reified C : Component> ItemComponentSynchronizer(
    predicate: PlayerPredicate,
    propagation: Propagation = Propagation.DISTANCE,
    noinline resolver: (EngineItem, C) -> Unit,
) = ComponentSynchronizer(
    SynchronizationTarget.ITEM,
    propagation,
    predicate,
    resolver,
)

private val LOGGER = LoggerFactory.getLogger("Engine Synchronization")

fun <T : Entity> ServerHandler.tickSynchronizationComponent(players: PlayerStorage, entity: T, component: Synchronizations<T> = entity.require()) {
    component.state.forEach { (id, state) ->
        if (state.dirty != null) {
            val synchronizer = state.synchronizer as ComponentSynchronizer<T, Component>
            val endpoint = synchronizer.endpoint
            val component = entity.getComponent(synchronizer.componentType) ?: error("Dirty component ${synchronizer.componentType} not found")
            val packet = ComponentSynchronizationPacket(entity.stringId, state.dirty?.interaction, component)

            fun broadcast(location: Location, player: EnginePlayer?) {
                var players = when (synchronizer.predicate) {
                    PlayerPredicate.ALL -> players
                    PlayerPredicate.SELF -> listOf(player)
                    PlayerPredicate.OTHERS -> players - player
                }.toList().filterNotNull()

                players = when(synchronizer.propagation) {
                    Propagation.DISTANCE -> filterNearestPlayers(location, playerSynchronizationRadius, players)
                    Propagation.GLOBAL -> players
                }

                players.forEach { endpoint.sendS2C(packet, it.id) }
            }

            when (synchronizer.target) {
                SynchronizationTarget.PLAYER -> {
                    broadcast(entity.location, entity as? EnginePlayer)
                }

                SynchronizationTarget.ITEM -> {
                    // Ищем владельца. В будущем можно будет рассылать обновление ближайшим игрокам в мире, если у предмета есть позиция
                    // Пока что о любых отклонениях предупреждаем
                    val owner = entity.get<HoldsBy>()?.owner ?: run {
                        LOGGER.warn("Не удалось синхронизировать состояние $id сущности $entity - не найден владелец")
                        return@forEach
                    }
                    broadcast(owner.location, entity as? EnginePlayer)
                }
            }

            state.dirty = null
        }
    }
}

class ComponentSynchronizationPacket<C : Component>(
    val id: String,
    val interaction: InteractionId? = null,
    val component: C,
) : Packet

// Player

val PLAYER_ARM_STATUS_SYNCHRONIZER = PlayerComponentSynchronizer<ArmStatus>(PlayerPredicate.OTHERS) { player, component -> player.replace(component.copy()) }
val PLAYER_CUSTOM_NAME_SYNCHRONIZER = PlayerComponentSynchronizer<DisplayName>(PlayerPredicate.ALL, Propagation.GLOBAL) { player, name -> player.customName = name.custom }
val PLAYER_SPEED_INTENTION_SYNCHRONIZER = PlayerComponentSynchronizer<MovementStatus>(PlayerPredicate.OTHERS) { player, status ->
    player.require<MovementStatus>().intention = status.intention
}
val PLAYER_NARRATION_SYNCHRONIZER = PlayerComponentSynchronizer<Narration>(PlayerPredicate.SELF) { player, narration ->
    val clientNarration = player.require<Narration>().messages
    if (clientNarration != narration.messages) {
        clientNarration.clear()
        clientNarration.addAll(narration.messages)
    }
}
val PLAYER_ATTRIBUTES_SYNCHRONIZER = PlayerComponentSynchronizer<PlayerAttributes>(PlayerPredicate.ALL) { player, component -> player.replace(component.copy()) }
val PLAYER_EQUIPMENT_SYNCHRONIZER = PlayerComponentSynchronizer<Equipment>(PlayerPredicate.ALL) { player, component -> player.replace(component.copy()) }
val PLAYER_MODEL_SYNCHRONIZER = PlayerComponentSynchronizer<PlayerModel>(PlayerPredicate.ALL) { player, component -> player.require<PlayerModel>().skinEyeY = component.skinEyeY }
val PLAYER_HEARING_SYNCHRONIZER = PlayerComponentSynchronizer<Hearing>(PlayerPredicate.SELF) { player, component -> player.require<Hearing>().tinnitus = component.tinnitus }

// Item

interface ItemSynchronizable

val ITEM_WRITABLE_SYNCHRONIZER = ItemComponentSynchronizer<Writable>(PlayerPredicate.ALL) { item, component -> item.replace(component.copy()) }
val ITEM_GUN_SYNCHRONIZER = ItemComponentSynchronizer<Gun>(PlayerPredicate.OTHERS) { item, component -> item.replace(component.copy()) }
val ITEM_FLASHLIGHT_SYNCHRONIZER = ItemComponentSynchronizer<Flashlight>(PlayerPredicate.OTHERS) { item, component -> item.get<Flashlight>()?.enabled = component.enabled }