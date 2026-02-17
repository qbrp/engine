package org.lain.engine.util.file

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    internal fun getSoundEvent(
        id: SoundEventId,
        namespace: FileNamespace,
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


internal fun compileSoundEvents(soundEvents: Map<String, SoundEventConfig>, namespace: FileNamespace): List<SoundEvent> {
    return soundEvents.map { (id, event) ->
        event.getSoundEvent(
            SoundEventId(namespacedId(namespace.id, id)),
            namespace
        )
    }
}