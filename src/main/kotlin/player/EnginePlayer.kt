package org.lain.engine.player

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.lain.cyberia.ecs.ComponentManager
import org.lain.engine.util.component.ComponentState
import org.lain.engine.util.component.Entity
import org.lain.engine.world.pos
import java.util.*

class EnginePlayer(
    val id: PlayerId,
    val state: ComponentState = ComponentState()
) : Entity, ComponentManager by state {
    override val stringId: String get() = id.toString()

    override fun toString(): String {
        return "EnginePlayer($username ($displayName), $id, $pos)"
    }
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