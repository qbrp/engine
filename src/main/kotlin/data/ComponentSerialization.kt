package org.lain.engine.data

import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.serializers.FormattedInstantSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import org.lain.cyberia.ecs.Component
import org.lain.engine.util.ecs.SerializationRegistry
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

fun SerializersModuleBuilder.polymorphicComponentSerializer() {
    val entries = SerializationRegistry.listEntries()
        .mapNotNull { (_, entry) ->
            val serializer = entry.serializer ?: return@mapNotNull null
            val replicationClass = entry.replicationClass ?: return@mapNotNull null
            replicationClass to serializer
        }

    polymorphic(Component::class) {
        entries.forEach { (replicationClass, serializer) ->
            @Suppress("UNCHECKED_CAST")
            subclass(replicationClass as KClass<Component>, serializer)
        }
    }
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}