package org.lain.engine.player

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.ReadComponentAccess
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.util.component.Entity
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.World
import java.util.*

class EnginePlayer(
    val id: PlayerId,
    val entity: EntityId,
    val world: World,
    var destroyed: Boolean = false
) : Entity {
    override val stringId: String get() = id.toString()

    override fun toString(): String {
        return with(world) { "EnginePlayer(${entity.username()} (${entity.displayNameString()}), $id)" }
    }
}

inline fun <reified T : Component> EnginePlayer.get(): T? = with(world) {
    entity.getComponent<T>()
}

fun <T : Component> EnginePlayer.getComponent(type: ComponentType<T>): T? = with(world) {
    componentManager.getComponent(entity, type)
}

inline fun <reified T : Component> EnginePlayer.require(): T = with(world) {
    entity.requireComponent<T>()
}

inline fun <reified T : Component> EnginePlayer.has(): Boolean = with(world) {
    entity.hasComponent<T>()
}

inline fun <reified T : Component> EnginePlayer.set(component: T): T = with(world) {
    entity.setComponent(component)
    component
}

inline fun <reified T : Component> EnginePlayer.getOrSet(noinline factory: () -> T): T = with(world) {
    entity.getComponent<T>() ?: factory().also { entity.setComponent(it) }
}

inline fun <reified T : Component> EnginePlayer.remove(): T? = with(world) {
    entity.removeComponent<T>()
}

@Deprecated("Use cyberia methods")
inline fun <reified T : Component> EnginePlayer.apply(noinline todo: T.() -> Unit): T = with(world) {
    entity.requireComponent<T>().apply(todo)
}

@Deprecated("Use cyberia methods")
inline fun <reified T : Component> EnginePlayer.handle(noinline todo: T.() -> Unit) {
    get<T>()?.todo()
}

@Deprecated("Use cyberia methods")
inline fun <reified T : Component, R> EnginePlayer.let(noinline todo: T.() -> R): R {
    return require<T>().todo()
}

@Deprecated("Use cyberia methods")
fun EnginePlayer.getComponents(): List<Component> = with(world) {
    componentManager.getComponents(entity, null)
}

@JvmInline
@Serializable(with = PlayerIdSerializer::class)
value class PlayerId(val value: UUID) {
    override fun toString(): String {
        return value.toString()
    }

    companion object {
        fun fromString(str: String): PlayerId = PlayerId(UUID.fromString(str))
    }
}

object PlayerIdSerializer : KSerializer<PlayerId> {
    override val descriptor = PrimitiveSerialDescriptor("PlayerId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PlayerId) {
        encoder.encodeString(value.value.toString())
    }

    override fun deserialize(decoder: Decoder): PlayerId {
        return PlayerId(UUID.fromString(decoder.decodeString()))
    }
}

fun randomPlayerId() = PlayerId(UUID.randomUUID())
