package org.lain.engine.mc.commands

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import org.lain.engine.player.BUILTIN_VOICE_PRESETS
import org.lain.engine.player.VoiceImage
import org.lain.engine.player.get
import org.lain.engine.player.markUpdated
import kotlin.reflect.KMutableProperty1

private fun <T : Any> voiceNode(
    name: String,
    argumentType: ArgumentType<T>,
    read: (ServerCommandContext, String) -> T,
    property: KMutableProperty1<VoiceImage, T>,
    suggestions: List<String> = emptyList(),
) = literal(name)
    .then(literal("set").then(argument("value", argumentType)
        .apply { if (suggestions.isNotEmpty()) suggests(StringListSuggestionProvider(suggestions)) }
        .executeCatching { ctx ->
            val player = ctx.requirePlayer()
            val value = read(ctx.command, "value")
            property.set(player.get<VoiceImage>() ?: friendlyError("Сначала выберите персонажа"), value)
            player.markUpdated<VoiceImage>()
            ctx.sendFeedback("Параметр голоса «$name» установлен в $value", true)
        }))
    .then(literal("reset").executeCatching { ctx ->
        val player = ctx.requirePlayer()
        property.set(player.get<VoiceImage>() ?: friendlyError("Сначала выберите персонажа"), property.get(VoiceImage()))
        player.markUpdated<VoiceImage>()
        ctx.sendFeedback("Параметр голоса «$name» сброшен", true)
    })

fun ServerCommandDispatcher.registerVoiceCommand() {
    register(
        literal("voice")
            .then(voiceNode("preset", StringArgumentType.string(), ServerCommandContext::getString, VoiceImage::preset, BUILTIN_VOICE_PRESETS))
            .then(voiceNode("randomization", FloatArgumentType.floatArg(), ServerCommandContext::getFloat, VoiceImage::randomization))
            .then(voiceNode("pitch", FloatArgumentType.floatArg(), ServerCommandContext::getFloat, VoiceImage::pitch))
            .then(voiceNode("speed", FloatArgumentType.floatArg(), ServerCommandContext::getFloat, VoiceImage::speed))
            .then(voiceNode("frequency", FloatArgumentType.floatArg(), ServerCommandContext::getFloat, VoiceImage::frequency))
    )
}