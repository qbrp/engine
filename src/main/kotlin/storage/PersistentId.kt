package org.lain.engine.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.lain.cyberia.ecs.Component
import org.lain.engine.world.VoxelPos
import java.util.*

@Serializable
data class PersistentIdComponent(val id: PersistentId) : Component

@Serializable
sealed interface PersistentId {
    override fun equals(other: Any?): Boolean
    override fun toString(): String
}

@Serializable
data class CustomPersistentId(val id: String) : PersistentId {
    override fun equals(other: Any?): Boolean {
        return other is CustomPersistentId && other.id == id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return id
    }
}

@Serializable
class Uuid internal constructor(
    @Serializable(with = JavaUuidSerializer::class) val javaUuid: UUID
): PersistentId {
    override fun equals(other: Any?): Boolean {
        return other is Uuid && other.javaUuid == javaUuid
    }
    override fun hashCode(): Int {
        return javaUuid.hashCode()
    }

    override fun toString(): String {
        return javaUuid.toString()
    }

    companion object {
        fun next(): Uuid = Uuid(UUID.randomUUID())
        fun from(string: String): Uuid = Uuid(UUID.fromString(string))
    }
}

object JavaUuidSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }
}

@Serializable
class VoxelPosId internal constructor(val pos: VoxelPos): PersistentId {
    override fun equals(other: Any?): Boolean {
        return other is VoxelPosId && pos == other.pos
    }

    override fun hashCode(): Int {
        return pos.hashCode()
    }

    override fun toString(): String {
        return pos.toShortString()
    }
}

fun persistentId(value: String) = CustomPersistentId(value)