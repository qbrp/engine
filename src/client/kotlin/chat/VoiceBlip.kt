package org.lain.engine.client.chat

import net.minecraft.util.FormattedCharSequence
import org.lain.engine.chat.MessageId
import org.lain.engine.client.GameSession
import org.lain.engine.player.PlayerAttributes
import org.lain.engine.player.PlayerId
import org.lain.engine.player.VoiceImage
import org.lain.engine.world.pos
import kotlin.compareTo
import kotlin.math.ceil
import kotlin.random.Random

class VoiceBlipManager(
    private val gameSession: GameSession
) {
    private data class PendingSound(
        val blip: VoiceBlip,
        val randomizationMultiplier: Float,
    )

    private val audioManager = gameSession.client.audioManager
    private val blips = mutableMapOf<MessageId, VoiceBlip>()
    private val pendingSounds = mutableListOf<PendingSound>()

    fun getOrCreate(message: MessageId, create: () -> VoiceBlip): VoiceBlip =
        blips.getOrPut(message, create)

    operator fun get(message: MessageId): VoiceBlip? = blips[message]

    fun tick(dt: Float) {
        blips.entries.removeIf { (_, blip) ->
            blip.step(dt) {
                pendingSounds += PendingSound(blip, 1f)
            }
            blip.settled
        }
    }

    fun playPendingSounds() {
        val sounds = pendingSounds.toList()
        pendingSounds.clear()

        sounds.forEach { sound ->
            val blip = sound.blip
            val player = gameSession.playerStorage.get(blip.player)
            player?.let {
                if (Random.nextFloat() < blip.properties.soundFrequency) {
                    audioManager.playVoiceBlipSound(
                        blip.properties,
                        player.pos,
                        sound.randomizationMultiplier
                    )
                }
            }
        }
    }

    fun remove(message: MessageId) {
        blips.remove(message)
    }

    fun clear() {
        blips.clear()
        pendingSounds.clear()
    }
}

data class VoiceProperties(
    val speed: Float = 0.6f,
    val pitch: Float = 1.7f,
    val randomization: Float = 0.7f,
    val soundSet: String = "c3",
    val soundFrequency: Float = 0.9f,
    val volume: Float
) {
    val syllableDur
        get() = VOICE_BLIP_MAX_DELAY - speed * (VOICE_BLIP_MAX_DELAY - VOICE_BLIP_MIN_DELAY)

    companion object {
        fun fromImage(volume: Float, image: VoiceImage) = VoiceProperties(
            speed = image.speed,
            pitch = image.pitch,
            randomization = image.randomization,
            soundSet = image.preset,
            soundFrequency = image.frequency,
            volume = volume,
        )
    }
}

data class SyllableChar(
    val parent: Syllable,
    var delay: Float,
)

data class VoiceBlipNode(
    val syllables: List<Syllable>,
    val chars: List<SyllableChar>,
    var processed: Int,
    var started: Int,
)

class VoiceBlip(
    val node: VoiceBlipNode,
    val properties: VoiceProperties,
    val player: PlayerId
) {
    val active: Boolean
        get() = node.processed < node.syllables.size

    val settled: Boolean
        get() = !active && node.chars.all { char -> char.delay <= CHAR_SHAKE_STOP_DELAY }

    fun step(dt: Float, onStarted: () -> Unit) {
        if (active) {
            startActiveSyllable(onStarted)
            val syllable = node.syllables[node.processed]

            syllable.delay -= dt
            if (syllable.delay <= 0f) {
                node.processed++
                startActiveSyllable(onStarted)
            }
        }

        node.chars.forEach { char ->
            if (char.delay > CHAR_SHAKE_STOP_DELAY) {
                char.delay -= dt
            }
        }
    }

    private fun startActiveSyllable(onStarted: () -> Unit) {
        if (!active || node.started != node.processed) return

        val syllable = node.syllables[node.processed]
        node.started++
        if (syllable.playSound) {
            onStarted()
        }
    }

    fun visibleSymbols(): Int {
        if (!active) return node.chars.size
        val syllable = node.syllables[node.processed]
        val remaining = ceil(syllable.delay / syllable.charDur).toInt()
        val visibleInSyllable = (syllable.charsCount - remaining + 1)
            .coerceIn(1, syllable.charsCount)
        return (syllable.startCharIndex + visibleInSyllable)
            .coerceAtMost(node.chars.size)
    }

    companion object {
        fun create(
            lines: List<FormattedCharSequence>,
            content: SpeechContent,
            player: PlayerId
        ): VoiceBlip {
            val properties = content.voiceProperties
            val syllables = lines.toSyllables(properties.syllableDur, content.indent)
            val processedSyllables = if (lines.isNotEmpty() && content.indent > 0) 1 else 0
            return VoiceBlip(
                VoiceBlipNode(
                    syllables = syllables,
                    chars = syllables.toChars(processedSyllables),
                    processed = processedSyllables,
                    started = processedSyllables,
                ),
                properties,
                player
            )
        }
    }
}

fun List<Syllable>.toChars(processed: Int = 0): List<SyllableChar> {
    require(processed in 0..size)

    var delay = 0f
    return flatMapIndexed { index, syllable ->
        List(syllable.charsCount) {
            if (index < processed) {
                SyllableChar(syllable, CHAR_SHAKE_STOP_DELAY)
            } else {
                delay += syllable.charDur
                SyllableChar(syllable, delay)
            }
        }
    }
}

const val VOICE_BLIP_MAX_DELAY = 4f
const val VOICE_BLIP_MIN_DELAY = 0.6f
const val CHAR_SHAKE_STOP_DELAY = -1.3f
const val MAX_CHAR_SHAKE = 0.8f
