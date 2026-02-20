package org.lain.engine.server

import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoBuf
import org.lain.engine.item.EngineItem
import org.lain.engine.item.HoldsBy
import org.lain.engine.item.ItemUuid
import org.lain.engine.item.Writable
import org.lain.engine.player.*
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketCodec
import org.lain.engine.util.*
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
    var tasks: MutableMap<Long, () -> Unit> = mutableMapOf(),
) : Component

val EnginePlayer.network
    get() = this.require<PlayerNetworkState>()

// Common synchronizers

data class Synchronizations<T : Entity>(val state: MutableMap<KClass<out Component>, State<T>> = mutableMapOf()) : Component {
    data class State<T : Entity>(var dirty: Boolean, val synchronizer: ComponentSynchronizer<T, *>)
}

inline fun <T : Entity, reified C : Component> Synchronizations<T>.submit(synchronizer: ComponentSynchronizer<T, C>) {
    state[C::class] = Synchronizations.State(false, synchronizer)
}

fun Entity.markDirty(componentClass: KClass<out Component>) {
    val state = require<Synchronizations<*>>().state[componentClass] ?: error("Component synchronizer for $componentClass not found")
    state.dirty = true
}

inline fun <reified C : Component> Entity.markDirty() {
    markDirty(C::class)
}

class ComponentSynchronizer<T : Entity, C : Component> @OptIn(ExperimentalSerializationApi::class) constructor(
    val componentClass: KClass<C>,
    val serializer: KSerializer<C>,
    val target: SynchronizationTarget,
    val radius: Int,
    val resolver: (T, C) -> Unit,
    val excludeEntity: Boolean = true,
    val endpoint: Endpoint<ComponentSynchronizationPacket<C>> = Endpoint(
        componentClass.simpleName!!.lowercase(),
        PacketCodec.Binary(
            {
                val id = readString()
                ComponentSynchronizationPacket<C>(id, ProtoBuf.decodeFromByteArray(serializer, readByteArray()))
            },
            {
                writeString(it.id)
                writeByteArray(ProtoBuf.encodeToByteArray(serializer, it.component))
            }
        )
    ),
)

@OptIn(InternalSerializationApi::class)
inline fun <T : Entity, reified C : Component> ComponentSynchronizer(
    target: SynchronizationTarget,
    radius: Int,
    excludeEntity: Boolean = false,
    noinline resolver: (T, C) -> Unit,
) = ComponentSynchronizer<T, C>(
    C::class,
    C::class.serializer(),
    target,
    radius,
    resolver,
)


inline fun <reified C : Component> PlayerComponentSynchronizer(
    global: Boolean = false,
    exclude: Boolean = false,
    noinline resolver: (EnginePlayer, C) -> Unit = { player, component -> player.replace(component) },
) = ComponentSynchronizer(
    SynchronizationTarget.PLAYER,
    if (!global) 48 else Int.MAX_VALUE,
    exclude,
    resolver,
)

inline fun <reified C : Component> ItemComponentSynchronizer(
    global: Boolean = false,
    exclude: Boolean = false,
    noinline resolver: (EngineItem, C) -> Unit = { item, component -> item.replace(component) },
) = ComponentSynchronizer(
    SynchronizationTarget.ITEM,
    if (!global) 48 else Int.MAX_VALUE,
    exclude,
    resolver,
)

enum class SynchronizationTarget {
    PLAYER, ITEM
}

private val LOGGER = LoggerFactory.getLogger("Engine Synchronization")

fun <T : Entity> ServerHandler.tickSynchronizationComponent(players: PlayerStorage, entity: T, component: Synchronizations<T> = entity.require()) {
    component.state.forEach { (id, state) ->
        if (state.dirty) {
            val synchronizer = state.synchronizer as ComponentSynchronizer<T, Component>
            val endpoint = synchronizer.endpoint
            val component = entity.getComponent(synchronizer.componentClass) ?: error("Dirty component ${synchronizer.componentClass} not found")
            val packet = ComponentSynchronizationPacket(entity.stringId, component)

            fun broadcast(location: Location, exclude: EnginePlayer? = null) {
                endpoint.broadcastInRadius(
                    location,
                    synchronizer.radius,
                    if (synchronizer.excludeEntity) {
                        listOfNotNull(exclude)
                    } else {
                        emptyList()
                    },
                    packet
                )
            }

            when (synchronizer.target) {
                SynchronizationTarget.PLAYER -> {
                    broadcast(entity.location, entity as? EnginePlayer)
                }

                SynchronizationTarget.ITEM -> {
                    // Ищем владельца. В будущем можно будет рассылать обновление ближайшим игрокам в мире, если у предмета есть позиция
                    // Пока что о любых отклонениях предупреждаем
                    val owner = entity.get<HoldsBy>()?.owner?.let { players.get(it) }  ?: run {
                        LOGGER.warn("Не удалось синхронизировать состояние $id сущности $entity - не найден владелец")
                        return@forEach
                    }
                    broadcast(owner.location, entity as? EnginePlayer)
                }
            }

            state.dirty = false
        }
    }
}

@Serializable
class ComponentSynchronizationPacket<C : Component>(val id: String, val component: C) : Packet

// Player

val PLAYER_ARM_STATUS_SYNCHRONIZER = PlayerComponentSynchronizer<ArmStatus>()
val PLAYER_CUSTOM_NAME_SYNCHRONIZER = PlayerComponentSynchronizer<DisplayName>(true) { player, name -> player.customName = name.custom }
val PLAYER_SPEED_INTENTION_SYNCHRONIZER = PlayerComponentSynchronizer<MovementStatus> { player, status -> player.require<MovementStatus>().intention = status.intention }

// Item

val ITEM_WRITABLE_SYNCHRONIZER = ItemComponentSynchronizer<Writable>()