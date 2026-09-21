package org.lain.engine.client.mc

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.kyori.adventure.platform.fabric.FabricClientAudiences
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.util.GsonHelper
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import org.lain.engine.client.EngineClient
import org.lain.engine.client.EngineMinecraftClient
import org.lain.engine.mc.TEXT_LOGGER
import org.lain.engine.mc.Text
import org.lain.engine.mc.removeLegacyFormattingCodes
import org.lain.engine.util.injectCaching

typealias JsonMc = GsonHelper

typealias ImmediateVertexConsumers = MultiBufferSource.BufferSource

val MinecraftClient = net.minecraft.client.Minecraft.getInstance()!!

val Minecraft.entityHitResult
    get() = this.hitResult as? EntityHitResult

val Minecraft.blockHitResult
    get() = this.hitResult as? BlockHitResult

val missingSpriteId get() = MissingTextureAtlasSprite.getLocation()

fun injectClient() = injectCaching<EngineClient>()

fun injectMinecraftClient() = injectCaching<EngineMinecraftClient>()

fun String.parseMiniMessageClient(): Text {
    val text = this.removeLegacyFormattingCodes()

    val component = MiniMessage.miniMessage().deserialize(text)
    return try {
        FabricClientAudiences.of().toNative(component)
    } catch (e: Throwable) {
        TEXT_LOGGER.error("Возникла ошибка при десериализации текста MiniMessage:\n$this", e)
        val jsonObject = JsonParser.parseString(GsonComponentSerializer.gson().serialize(component))
        ComponentSerialization.CODEC
            .parse(JsonOps.INSTANCE, jsonObject)
            .getOrThrow()
    }
}
