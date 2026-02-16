package org.lain.engine.server

import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoBuf
import org.lain.engine.item.ItemUuid
import org.lain.engine.player.ArmStatus
import org.lain.engine.player.DisplayName
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.customName
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketCodec
import org.lain.engine.util.Component
import org.lain.engine.util.Entity
import org.lain.engine.util.replace
import org.lain.engine.util.require
import org.lain.engine.world.location
import kotlin.reflect.KClass

data class PlayerSynchronizationComponent(
    var authorized: Boolean,
    val synchronizedPlayers: MutableList<EnginePlayer> = mutableListOf(),
    val synchronizedItems: MutableList<ItemUuid> = mutableListOf()
) : Component

val EnginePlayer.synchronization
    get() = this.require<PlayerSynchronizationComponent>()

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

enum class SynchronizationTarget {
    PLAYER
}

fun <T : Entity> ServerHandler.tickSynchronizationComponent(entity: T, component: Synchronizations<T> = entity.require()) {
    component.state.forEach { (id, state) ->
        if (state.dirty) {
            val synchronizer = state.synchronizer as ComponentSynchronizer<T, Component>
            val endpoint = synchronizer.endpoint
            when (synchronizer.target) {
                SynchronizationTarget.PLAYER -> {
                    val component = entity.getComponent(synchronizer.componentClass) ?: error("Dirty component ${synchronizer.componentClass} not found")
                    val packet = ComponentSynchronizationPacket(entity.stringId, component)
                    endpoint.broadcastInRadius(
                        entity.location,
                        synchronizer.radius,
                        if (synchronizer.excludeEntity) {
                            listOfNotNull(entity as? EnginePlayer)
                        } else {
                            emptyList()
                        },
                    ) { packet }
                    state.dirty = false
                }
            }
        }
    }
}

@Serializable
class ComponentSynchronizationPacket<C : Component>(val id: String, val component: C) : Packet

// Player

val PLAYER_ARM_STATUS_SYNCHRONIZER = PlayerComponentSynchronizer<ArmStatus>()