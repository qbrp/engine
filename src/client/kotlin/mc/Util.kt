package org.lain.engine.client.mc

import com.google.gson.JsonParser
import com.mojang.authlib.minecraft.client.MinecraftClient
import com.mojang.serialization.JsonOps
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.item.ClientItem
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
import org.lain.engine.util.inject

typealias ItemAsset = ClientItem

typealias ItemAssetProperties = ClientItem.Properties

typealias JsonMc = GsonHelper

typealias ImmediateVertexConsumers = MultiBufferSource.BufferSource

val MinecraftClient = net.minecraft.client.Minecraft.getInstance()!!

val Minecraft.entityHitResult
    get() = this.hitResult as? EntityHitResult

val Minecraft.blockHitResult
    get() = this.hitResult as? BlockHitResult

val MissingSpriteId get() = MissingTextureAtlasSprite.getLocation()

fun injectClient() = inject<EngineClient>()

fun injectMinecraftClient() = inject<EngineMinecraftClient>()

fun String.parseMiniMessageClient(): Text {
    val text = this.removeLegacyFormattingCodes()

    val component = MiniMessage.miniMessage().deserialize(text)
    return try {
        MinecraftClientAudiences.of().asNative(component)
    } catch (e: Throwable) {
        TEXT_LOGGER.error("Возникла ошибка при десериализации текста MiniMessage:\n$this", e)
        val jsonObject = JsonParser.parseString(GsonComponentSerializer.gson().serialize(component))
        ComponentSerialization.CODEC
            .parse(JsonOps.INSTANCE, jsonObject)
            .getOrThrow()
    }
}