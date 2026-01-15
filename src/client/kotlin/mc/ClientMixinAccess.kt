package org.lain.engine.client.mc

import net.minecraft.client.render.Camera
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import org.lain.engine.client.chat.AcceptedMessage
import org.lain.engine.client.chat.LiteralSystemEngineChatMessage
import org.lain.engine.client.mc.render.TransformationsEditorScreen
import org.lain.engine.client.render.CD
import org.lain.engine.client.render.VOICE_WARNING
import org.lain.engine.client.resources.ResourceList
import org.lain.engine.client.resources.findAssets
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.util.Timestamp
import org.lain.engine.util.injectValue
import org.lain.engine.util.roundToInt
import org.lwjgl.glfw.GLFW

object ClientMixinAccess {
    private val client by injectClient()
    private var resources: ResourceList? = null
    private var developerModeKeyPressedTick = 0L
    var chatClipboardCopyTicksElapsed = 0

    fun tick() {
        chatClipboardCopyTicksElapsed += 1
    }

    fun isEngineLoaded(): Boolean {
        val isPlayerInstantiated = client.gameSession?.mainPlayer != null
        return isPlayerInstantiated
    }

    fun onScroll(vertical: Float) {
        client.onScroll(vertical)
    }

    fun isScrollAllowed() = client.gameSession?.movementManager?.locked ?: true

    fun isCrosshairAttackIndicatorVisible() = client.options.crosshairIndicatorVisible

    fun onKey(key: Int) = with(client) {
        if (developerMode && (ticks - developerModeKeyPressedTick > 20)) {
            developerModeKeyPressedTick = ticks
            if (key == GLFW.GLFW_KEY_1) {
                audioManager.playPigScreamSound()
                applyLittleNotification(
                    LittleNotification(
                        "Проигран звук",
                        "pig-scream.ogg",
                        sprite = CD,
                    ),
                )
            } else if (key == GLFW.GLFW_KEY_2) {
                val player = MinecraftClient.player ?: return
                val mainHandItemStack = player.mainHandStack
                val offHandItemStack = player.offHandStack
                val itemStack = if (mainHandItemStack.isEmpty) offHandItemStack else mainHandItemStack
                if (client.developerMode && player.activeItem != null && !itemStack.isEmpty) {
                    MinecraftClient.setScreen(TransformationsEditorScreen(itemStack))
                }
            } else if (key == GLFW.GLFW_KEY_3) {
                val gameSession = gameSession ?: return@with
                val start = Timestamp()
                repeat(100) {
                    gameSession.chatManager.addMessage(
                        LiteralSystemEngineChatMessage(gameSession, roundToInt(Math.random() * 999999).toString())
                    )
                }

                val end = start.timeElapsed()
                audioManager.playUiNotificationSound()
                applyLittleNotification(
                    LittleNotification(
                        "Добавлено 100 случайных сообщений",
                        "Время: ${start.timeElapsed()} мл.",
                        sprite = VOICE_WARNING,
                    ),
                )
            }
        }
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

    fun deleteChatMessage(message: AcceptedMessage) = client.gameSession?.chatManager?.deleteMessage(message.id)

    fun sendingMessageClosesChat() = client.options.chatInputSendClosesChat

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