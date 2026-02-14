package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.chat.EngineChat
import org.lain.engine.util.*
import org.slf4j.LoggerFactory
import kotlin.random.Random

@Serializable
data class VoiceApparatus(
    var inputVolume: Float, // Как сильно ХОЧЕТ говорить игрок. Значение от 0.0 до maxVolume
    var tiredness: Float = 0f,
    var lastNotificationTick: Long = 6000,
    val baseVolume: Float? = null, // Стандартное значение громкости, которое считается ОБЫЧНОЙ речью. Выше - ГРОМКАЯ, ниже - ТИХАЯ
    val maxVolume: Float? = null, // Эти значения изначально хранятся в engineServer
    val minVolume: Float? = null,
) : Component

val EnginePlayer.volume: Float
    get() {
        val voiceApparatus = require<VoiceApparatus>()
        val defaults = require<DefaultPlayerAttributes>()

        return getRealVolume(
            voiceApparatus.tiredness,
            voiceApparatus.minVolume ?: defaults.minVolume,
            voiceApparatus.maxVolume ?: defaults.maxVolume,
            voiceApparatus.inputVolume,
            has<VoiceLoose>()
        )
    }

// Как сильно МОЖЕТ говорить игрок. Влияние усталости и экстраполяция на minVolume..mixVolume
fun getRealVolume(
    tiredness: Float,
    minVolume: Float,
    maxVolume: Float,
    inputVolume: Float,
    loosen: Boolean
): Float {
    val tirednessMultiplier = if (!loosen) {
        0.8f
    } else {
        0.9f
    }

    val max = ((1 - tiredness * tirednessMultiplier) * maxVolume)
    val outputVolume = (maxVolume - minVolume) * inputVolume + minVolume
    return outputVolume.coerceIn(0f, max)
}

@Serializable
data class VoiceLoose(
    val ticksToRegeneration: Int,
    var ticks: Int = 0
) : Component {
    val secondPhase
        get() = ticks > ticksToRegeneration / 2
}

private val VOICE_BREAK_LOGGER = LoggerFactory.getLogger("Engine Voice Break")

val DefaultPlayerAttributes.playerBaseInputVolume
    get() = this.baseVolume - this.minVolume

val EnginePlayer.isVoiceLoosed get() = this.has<VoiceLoose>()

val EnginePlayer.canSpeakUnlimited get() = this.get<VoiceLoose>().let { it == null || it.secondPhase }

fun voiceBrokenContent(content: String, from: Float): String {
    var i = Random.nextInt((content.length * from).toInt(), content.length)
    val start = i
    val output = StringBuilder()
    var cursed = 0f
    while (cursed < 1f) {
        if (Random.nextFloat() / 2 < cursed) {
            val random = Random.nextInt(0, 2)
            when (random) {
                0 -> {
                    val l = Random.nextInt(1, 3)
                    output.append("к" + "х".repeat(l))
                    i += l + 1
                }
                1 -> {
                    output.append("#")
                    i++
                }
                2 -> {
                    val l = Random.nextInt(1, 3)
                    output.append("~".repeat(l))
                    i += l
                }
            }
        }
        cursed += Random.nextFloat() * 0.15f
    }
    return content.take(start) + output.toString()
}

private val VOICE_LOOSE_MESSAGES = listOf("кх", "кх-к", "кхх", "к")
fun voiceLoosenContent(): String {
    return VOICE_LOOSE_MESSAGES.random() + ".".repeat(Random.nextInt(0, 4))
}

/**
 * @param breakThreshold С какого значения усталости линейно повышается шанс сорвать голос
 * @param breakChance Шанс сорвать голос при максимальной усталости
 * @param regenerationTime Восстановление голоса в тик
 * @param regenerationTimeRandom Случайный разброс восстановления голоса
 * @param tirednessGain Усиление усталости голоса при полной громкости
 * @param tirednessDecreaseRate Уменьшение усталости голоса в 1 тик
 * @param tirednessThreshold Выше какой громкости голосовой аппарат начинает уставать
 * @param breakWarningThreshold Выше какого значения отправлять уведомление о перенапряжении голоса
 */
data class VocalSettings(
    val breakThreshold: Float = 0.5f,
    val breakChance: Float = 0.1f,
    val regenerationTime: Int = 140000,
    val regenerationTimeRandom: Float = 0.5f,
    val tirednessThreshold: Float = 0.6f,
    val tirednessGain: Float = 0.0125f,
    val tirednessDecreaseRate: Float = 1.66E-4f,
    val breakWarningThreshold: Float = tirednessThreshold * 0.9f
)

fun EnginePlayer.updateVoiceApparatus(
    chat: EngineChat,
    volume: Float = this.volume,
    settings: VocalSettings
): Boolean = with(settings) {
    val voiceApparatus = require<VoiceApparatus>()
    val defaults = require<DefaultPlayerAttributes>()
    val maxVolume = voiceApparatus.maxVolume ?: defaults.maxVolume
    val tiredness = voiceApparatus.tiredness

    val tirednessAddition = (volume - tirednessThreshold).coerceAtLeast(0f) / (maxVolume - tirednessThreshold) * tirednessGain
    voiceApparatus.tiredness = (tiredness + tirednessAddition).coerceIn(0f, 1f)

    val looseChanceMultiplier = (tiredness - breakThreshold).coerceAtLeast(0f) / breakThreshold
    val loose = Random.nextFloat() < (breakChance * looseChanceMultiplier)
    val breakVoice = loose && !isVoiceLoosed
    if (breakVoice) {
        require<VoiceApparatus>().tiredness = 1f
        val component = VoiceLoose(
            (regenerationTime * (1 - regenerationTimeRandom * Random.nextFloat())).toInt())
        set(component)
        VOICE_BREAK_LOGGER.info("$username сломал голос! | VoiceApparatus: $this | VoiceLoose: $component")
        chat.sendSystemMessage(
            "<yellow>Ой, кажется, вы сорвали голос! Следует быть поаккуратнее с криками.</yellow>",
            this@updateVoiceApparatus
        )
    }
    return@with breakVoice
}

fun updatePlayerVoice(
    player: EnginePlayer,
    chat: EngineChat,
    settings: VocalSettings,
    tickRate: Int = 1,
) = with(settings) {
    val voiceLoose = player.get<VoiceLoose>()
    val voiceApparatus = player.require<VoiceApparatus>()
    var canReduceTiredness = voiceLoose == null
    if (voiceLoose != null) {
        voiceLoose.ticks += tickRate
        val ticks = voiceLoose.ticks
        val ticksToRegen = voiceLoose.ticksToRegeneration

        if (voiceLoose.secondPhase) {
            canReduceTiredness = true
        }
        if (ticks > ticksToRegen) {
            player.remove<VoiceLoose>()
        }
    }
    val tiredness = voiceApparatus.tiredness
    if (canReduceTiredness && tiredness > 0f) {
        voiceApparatus.tiredness = (tiredness - tirednessDecreaseRate).coerceAtLeast(0f)
    }

    // 5 минут
    if (tiredness > breakWarningThreshold && voiceApparatus.lastNotificationTick > 6000) {
        voiceApparatus.lastNotificationTick = 0
        if (voiceLoose == null) {
            chat.sendSystemMessage(
                "<yellow>Аккуратнее! Кажется, вы вот-вот сорвёте голос.</yellow>",
                player
            )
        }
    }
    if (tiredness < breakWarningThreshold) {
        voiceApparatus.lastNotificationTick += tickRate
    }
}