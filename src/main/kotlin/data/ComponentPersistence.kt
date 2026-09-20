@file:OptIn(ExperimentalSerializationApi::class)
package org.lain.engine.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.script.ScriptValue
import org.lain.engine.util.ecs.SerializationRegistry

private val CBOR = Cbor { ignoreUnknownKeys = true }

@JvmInline
@Serializable
value class ComponentByteArray(val array: ByteArray)

@Serializable
sealed interface PersistentComponentDto {
    val id: String

    @Serializable
    @SerialName("payload")
    data class Payload(
        override val id: String,
        val payload: ComponentByteArray,
    ) : PersistentComponentDto

    @Serializable
    @SerialName("script")
    data class Script(
        val scriptId: ScriptComponentId,
        val value: ScriptValue,
    ) : PersistentComponentDto {
        override val id: String
            get() = scriptId.toString()
    }

    fun encode(): ByteArray = Cbor.encodeToByteArray(this)

    companion object {
        fun decode(array: ByteArray): PersistentComponentDto =
            Cbor.decodeFromByteArray<PersistentComponentDto>(array)
    }
}

fun PersistentComponentDto.decode(): ComponentSnapshot = when (this) {
    is PersistentComponentDto.Payload -> {
        val entry = SerializationRegistry.get(id)
            ?: error("Serialization entry for component type $id does not exist")
        val serializer = entry.serializer
            ?: error("Serializer for component type $id is not registered")
        ComponentSnapshot.Kotlin(
            Cbor.decodeFromByteArray(serializer, payload.array)
        )
    }
    is PersistentComponentDto.Script -> {
        ComponentSnapshot.Script(scriptId, value)
    }
}

fun ComponentSnapshot.serializeToPersistentDto(): PersistentComponentDto = when (this) {
    is ComponentSnapshot.Script -> PersistentComponentDto.Script(id, value)
    is ComponentSnapshot.Kotlin<*> -> {
        val id = componentTypeOf(component).id
        val entry = SerializationRegistry.get(id)
            ?: error("Serialization entry for component type $id does not exist")
        val serializer = entry.serializer
            ?: error("Serializer for component type $id is not registered")
        PersistentComponentDto.Payload(
            id,
            ComponentByteArray(
                CBOR.encodeToByteArray(
                    serializer,
                    component
                )
            )
        )
    }
}
