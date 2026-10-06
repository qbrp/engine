@file:OptIn(ExperimentalSerializationApi::class)
package org.lain.engine.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.script.ScriptValue

private val LEGACY_COMPONENT_CBOR = Cbor { ignoreUnknownKeys = true }

@Serializable
internal sealed interface LegacyPersistentComponentDto {
    val id: String

    @Serializable
    @SerialName("payload")
    data class Payload(
        override val id: String,
        val payload: ComponentByteArray,
    ) : LegacyPersistentComponentDto

    @Serializable
    @SerialName("script")
    data class Script(
        val scriptId: ScriptComponentId,
        val value: ScriptValue,
    ) : LegacyPersistentComponentDto {
        override val id: String
            get() = scriptId.toString()
    }
}

internal fun decodeLegacyComponentPayload(
    array: ByteArray,
    id: RawEngineId,
): ComponentPayload {
    val legacy = LEGACY_COMPONENT_CBOR.decodeFromByteArray<LegacyPersistentComponentDto>(array)
    require(legacy.id == id.id) {
        "Component payload id ${legacy.id} does not match record id ${id.id}"
    }
    return legacy.toComponentPayload()
}

private fun LegacyPersistentComponentDto.toComponentPayload(): ComponentPayload = when (this) {
    is LegacyPersistentComponentDto.Payload -> ComponentPayload.Kotlin(payload)
    is LegacyPersistentComponentDto.Script -> ComponentPayload.Script.Cbor(
        ComponentByteArray(LEGACY_COMPONENT_CBOR.encodeToByteArray<ScriptValue>(value))
    )
}
