package org.lain.engine.client.mc

import net.minecraft.client.render.Camera
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import org.lain.engine.client.chat.ChatBubble
import org.lain.engine.client.chat.EngineChatMessage
import org.lain.engine.client.resources.ResourceList
import org.lain.engine.client.resources.findAssets
import org.lain.engine.util.injectValue

object ClientMixinAccess {
    private val client by injectClient()
    private var resources: ResourceList? = null

    fun isEngineLoaded(): Boolean {
        val isPlayerInstantiated = client.gameSession?.mainPlayer != null
        return isPlayerInstantiated
    }

    fun onScroll(vertical: Float) {
        client.onScroll(vertical)
    }

    fun isScrollAllowed() = client.gameSession?.movementManager?.locked ?: true

    fun isCrosshairAttackIndicatorVisible() = client.options.crosshairIndicatorVisible

    fun onKey(key: Int) {
        client.onKey(key)
    }

    fun sendChatMessage(content: String) {
        val gameSession = client.gameSession ?: return
        gameSession.chatManager.sendMessage(content)
    }

    fun getResourceList(): ResourceList {
        return resources ?: findAssets().also { resources = it }
    }

    fun createResourceList(): ResourceList {
        resources = findAssets()
        return resources!!
    }

    fun getKeybindManager(): KeybindManager = injectValue()

    fun deleteChatMessage(message: EngineChatMessage) = client.gameSession?.chatManager?.deleteMessage(message.id)

    fun renderChatBubbles(
        matrices: MatrixStack,
        camera: Camera,
        vertexConsumers: VertexConsumerProvider.Immediate,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
    ) {
        org.lain.engine.client.mc.render.renderChatBubbles(
            matrices,
            camera,
            vertexConsumers,
            MinecraftClient.textRenderer,
            cameraX,
            cameraY,
            cameraZ,
            client.options.chatBubbleScale,
            client.options.chatBubbleHeight,
            client.options.chatBubbleBackgroundOpacity,
            client.gameSession?.chatBubbleList?.bubbles ?: emptyList(),
            MinecraftClient.renderTickCounter.fixedDeltaTicks
        )
    }
}