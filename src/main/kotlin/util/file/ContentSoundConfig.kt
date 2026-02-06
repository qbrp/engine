package org.lain.engine.util.file

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.lain.engine.item.SoundEvent
import org.lain.engine.item.SoundEventId
import org.lain.engine.item.SoundId
import org.lain.engine.item.SoundSource

@Serializable
data class SoundEventConfig(
    @SerialName("plays") val sounds: YamlNode,
    val volume: Float? = null,
    val pitch: Float? = null,
    val distance: Int? = null,
    @SerialName("pitch_random") val pitchRandom: Float? = null
) {
    fun getSoundEvent(
        id: SoundEventId,
        namespace: NamespaceContents,
        entries: List<SoundEntry> = deserializeSoundEntries(sounds)
    ): SoundEvent {
        return SoundEvent(
            id,
            entries.map {
                SoundSource(
                    SoundId(it.audio.replaceToRelative(namespace)),
                    it.volume ?: volume ?: 1f,
                    it.pitch ?: pitch ?: 1f,
                    it.weight,
                    it.distance ?: distance ?: 16,
                    it.pitchRandom ?: pitchRandom ?: 0f
                )
            }
        )
    }
}

@Serializable
data class SoundEntry(
    val audio: String,
    val weight: Int = 1,
    val volume: Float? = null,
    val pitch: Float? = null,
    val distance: Int? = null,
    @SerialName("pitch_random") val pitchRandom: Float? = null
)

fun deserializeSoundEntries(entries: YamlNode): List<SoundEntry> {
    val stringList = runCatching { Yaml.default.decodeFromYamlNode<List<String>>(entries) }
    val soundEntryList = runCatching { Yaml.default.decodeFromYamlNode<List<SoundEntry>>(entries) }
    return stringList.getOrNull()?.map { SoundEntry(it, 1) } ?: soundEntryList.getOrThrow()
}


fun compileSoundEventConfig(id: String, event: SoundEventConfig, namespace: NamespaceContents): SoundEvent {
    val eventId = NamespaceSoundEventId(namespace.id, id)
    return event.getSoundEvent(eventId, namespace)
}