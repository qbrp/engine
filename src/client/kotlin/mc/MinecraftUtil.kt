package org.lain.engine.client.mc

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.minecraft.text.Text
import net.minecraft.text.TextCodecs
import org.lain.engine.client.EngineClient
import org.lain.engine.util.inject
import org.lain.engine.util.text.TEXT_LOGGER
import org.lain.engine.util.text.removeLegacyFormattingCodes

val MinecraftClient = net.minecraft.client.MinecraftClient.getInstance()!!

fun injectClient() = inject<EngineClient>()

fun String.parseMiniMessageClient(): Text {
    val text = this.removeLegacyFormattingCodes()

    val component = MiniMessage.miniMessage().deserialize(text)
    return try {
        MinecraftClientAudiences.of().asNative(component)
    } catch (e: Throwable) {
        TEXT_LOGGER.error("Возникла ошибка при десериализации текста MiniMessage:\n$this", e)
        val jsonObject = JsonParser.parseString(GsonComponentSerializer.gson().serialize(component))
        TextCodecs.CODEC
            .parse(JsonOps.INSTANCE, jsonObject)
            .getOrThrow()
    }
}