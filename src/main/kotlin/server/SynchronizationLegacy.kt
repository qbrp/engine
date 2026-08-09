package org.lain.engine.server

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import org.lain.cyberia.ecs.*
import org.lain.engine.player.*
import org.lain.engine.storage.PersistentId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketCodec
import org.lain.engine.util.component.Entity
import org.lain.engine.util.math.filterNearestPlayers
import org.lain.engine.world.*
import kotlin.reflect.KClass

// Common synchronizers

data class Synchronizations<T : Entity>(val state: MutableMap<KClass<out Component>, State<T>> = mutableMapOf()) : Component {
    data class State<T : Entity>(var dirty: Boolean, val synchronizer: ComponentSynchronizer<T, *>)
}

inline fun <T : Entity, reified C : Component> Synchronizations<T>.submit(synchronizer: ComponentSynchronizer<T, C>) {
    state[C::class] = Synchronizations.State(false, synchronizer)
}

fun Entity.markDirty(componentClass: KClass<out Component>) {
    val synchronizations = when (this) {
        is EnginePlayer -> require<Synchronizations<*>>()
        else -> error("Synchronizations are not available for $this")
    }
    val state = synchronizations.state[componentClass] ?: error("Component synchronizer for $componentClass not found")
    state.dirty = true
}

inline fun <reified C : Component> Entity.markDirty() {
    markDirty(C::class)
}

enum class PlayerPredicate {
    ALL, SELF, OTHERS
}

enum class Propagation {
    DISTANCE, GLOBAL
}

class ComponentSynchronizer<T : Entity, C : Component> @OptIn(ExperimentalSerializationApi::class) constructor(
    val componentType: ComponentType<C>,
    val serializer: KSerializer<C>,
    val propagation: Propagation,
    val resolver: (T, C) -> Unit,
    val predicate: PlayerPredicate,
    val endpoint: Endpoint<ComponentSynchronizationPacket<C>> = Endpoint(
        componentType.id,
        PacketCodec.Binary(
            {
                val id = readUtf()
                ComponentSynchronizationPacket<C>(
                    id,
                    ProtoBuf.decodeFromByteArray(serializer, readByteArray()),
                )
            },
            {
                writeUtf(it.id)
                writeByteArray(ProtoBuf.encodeToByteArray(serializer, it.component))
            }
        )
    ),
)

@OptIn(InternalSerializationApi::class)
inline fun <T : Entity, reified C : Component> ComponentSynchronizer(
    propagation: Propagation,
    predicate: PlayerPredicate,
    noinline resolver: (T, C) -> Unit,
) = ComponentSynchronizer<T, C>(
    componentTypeOf(C::class),
    C::class.serializer(),
    propagation,
    resolver,
    predicate
)

inline fun <reified C : Component> PlayerComponentSynchronizer(
    predicate: PlayerPredicate,
    propagation: Propagation = Propagation.DISTANCE,
    noinline resolver: (EnginePlayer, C) -> Unit,
) = ComponentSynchronizer(
    propagation,
    predicate,
    resolver,
)


fun <T : Entity> ServerHandler.tickSynchronizationComponent(players: PlayerStorage, entity: T, component: Synchronizations<T> = (entity as EnginePlayer).require()) {
    TODO()
    component.state.forEach { (id, state) ->
        if (state.dirty) {
            val synchronizer = state.synchronizer as ComponentSynchronizer<T, Component>
            val endpoint = synchronizer.endpoint
            val component = (entity as EnginePlayer).getComponent(synchronizer.componentType) ?: error("Dirty component ${synchronizer.componentType} not found")
            val packet = ComponentSynchronizationPacket(entity.stringId, component)

            fun broadcast(world: World, location: Location, player: EnginePlayer?) {
                var players = when (synchronizer.predicate) {
                    PlayerPredicate.ALL -> players
                    PlayerPredicate.SELF -> listOf(player)
                    PlayerPredicate.OTHERS -> players - player
                }.toList().filterNotNull()

                players = when(synchronizer.propagation) {
                    Propagation.DISTANCE -> filterNearestPlayers(world, location.position, playerSynchronizationRadius, players)
                    Propagation.GLOBAL -> players
                }

                players.forEach { endpoint.sendS2C(packet, it.id) }
            }

            val player = entity as EnginePlayer
            broadcast(player.world, player.location, entity as? EnginePlayer)
            state.dirty = false
        }
    }
}

class ComponentSynchronizationPacket<C : Component>(
    val id: String,
    val component: C,
) : Packet

// Player

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
val PLAYER_ATTRIBUTES_SYNCHRONIZER = PlayerComponentSynchronizer<PlayerAttributes>(PlayerPredicate.ALL) { player, component ->
    with(player.world) { player.entity.setComponent(component.copy()) }
}
val PLAYER_MODEL_SYNCHRONIZER = PlayerComponentSynchronizer<EnginePlayerModel>(PlayerPredicate.ALL) { player, component -> player.require<EnginePlayerModel>().skinEyeY = component.skinEyeY }
val PLAYER_HEARING_SYNCHRONIZER = PlayerComponentSynchronizer<Hearing>(PlayerPredicate.SELF) { player, component -> player.require<Hearing>().tinnitus = component.tinnitus }
