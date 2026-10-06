package org.lain.engine.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val LEGACY_WORLD_JSON = Json {
    allowStructuredMapKeys = true
    ignoreUnknownKeys = true
}

@Serializable
private data class LegacyWorldPersistent(
    val components: List<LegacyPersistentComponentDto.Script>
)

internal fun decodeLegacyWorldPersistent(value: String): WorldPersistent {
    val legacy = LEGACY_WORLD_JSON.decodeFromString<LegacyWorldPersistent>(value)
    return WorldPersistent(
        legacy.components.map { component ->
            ComponentPersistentRecord(
                component.id.asRawEngineId(),
                0,
                ComponentPayload.Script.Json(component.value),
                null,
            )
        }
    )
}
