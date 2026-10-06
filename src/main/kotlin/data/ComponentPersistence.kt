@file:OptIn(ExperimentalSerializationApi::class)
package org.lain.engine.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.toScriptComponentId
import org.lain.engine.util.ecs.SerializationRegistry

private val CBOR = Cbor { ignoreUnknownKeys = true }

fun ComponentPayload.encode(): ByteArray = CBOR.encodeToByteArray(this)

fun decodeComponentPayload(array: ByteArray, id: RawEngineId): ComponentPayload {
    return try {
        CBOR.decodeFromByteArray<ComponentPayload>(array)
    } catch (currentFormatException: Exception) {
        try {
            decodeLegacyComponentPayload(array, id)
        } catch (legacyFormatException: Exception) {
            legacyFormatException.addSuppressed(currentFormatException)
            throw legacyFormatException
        }
    }
}

fun ComponentPayload.decode(id: RawEngineId): ComponentSnapshot = when (this) {
    is ComponentPayload.Kotlin -> {
        val entry = SerializationRegistry.get(id.id)
            ?: error("Serialization entry for component type $id does not exist")
        val serializer = entry.serializer
            ?: error("Serializer for component type $id is not registered")
        ComponentSnapshot.Kotlin(
            CBOR.decodeFromByteArray(serializer, cbor.array)
        )
    }

    is ComponentPayload.Script.Json -> {
        ComponentSnapshot.Script(id.parse().toScriptComponentId(), value)
    }

    is ComponentPayload.Script.Cbor -> {
        ComponentSnapshot.Script(
            id.parse().toScriptComponentId(),
            CBOR.decodeFromByteArray<ScriptValue>(cbor.array)
        )
    }
}

fun ComponentSnapshot.Script.toJsonComponentPayload(): ComponentPayload.Script.Json =
    ComponentPayload.Script.Json(value)

fun ComponentSnapshot.serializeToComponentPayload(): ComponentPayload = when (this) {
    is ComponentSnapshot.Script -> ComponentPayload.Script.Cbor(
        ComponentByteArray(CBOR.encodeToByteArray<ScriptValue>(value))
    )

    is ComponentSnapshot.Kotlin<*> -> {
        val id = componentTypeOf(component).id
        val entry = SerializationRegistry.get(id)
            ?: error("Serialization entry for component type $id does not exist")
        val serializer = entry.serializer
            ?: error("Serializer for component type $id is not registered")
        ComponentPayload.Kotlin(
            ComponentByteArray(
                CBOR.encodeToByteArray(
                    serializer,
                    component
                )
            )
        )
    }
}
