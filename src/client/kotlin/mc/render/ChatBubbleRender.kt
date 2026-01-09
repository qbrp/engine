package org.lain.engine.client.mc.render

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.Camera
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import org.lain.engine.client.chat.ChatBubble
import org.lain.engine.client.chat.updateChatBubble
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.util.Color
import org.lain.engine.util.text.toMinecraft

private val textCache = TextCache()

fun renderChatBubbles(
    matrices: MatrixStack,
    camera: Camera,
    vertexConsumers: VertexConsumerProvider.Immediate,
    textRender: TextRenderer,
    cameraX: Double,
    cameraY: Double,
    cameraZ: Double,
    scale: Float,
    height: Float,
    backgroundOpacity: Float,
    bubbles: List<ChatBubble>,
    dt: Float,
) {
    val client = MinecraftClient
    if (client.player == null || client.world == null) {
        return
    }

    for (bubble in bubbles) {
        updateChatBubble(bubble, dt, height)
        val bubblePos = bubble.pos
        val alpha = bubble.opacity
        if (alpha <= 0f) {
            continue
        }

        matrices.push()
        matrices.translate(
            (bubblePos.x - cameraX),
            (bubblePos.y - cameraY) + 0.07f,
            (bubblePos.z - cameraZ)
        )
        matrices.multiply(camera.rotation)
        matrices.scale(scale, -scale, scale)

        var y = 0f
        for (line in bubble.lines) {
            val text = textCache.get(line)
            val offset = (-client.textRenderer.getWidth(text)) / 2.0f

            val textColor = Color.WHITE.withAlpha((alpha * 255).toInt())
            val backgroundColor = Color.BLACK.withAlpha((backgroundOpacity * alpha * 255f).toInt())
            y += textRender.fontHeight

            val matrix = matrices.peek().positionMatrix
            client.textRenderer.draw(
                text,
                offset,
                y,
                textColor.integer,
                false,
                matrix,
                vertexConsumers,
                TextRenderer.TextLayerType.NORMAL,
                backgroundColor.integer,
                LightmapTextureManager.MAX_LIGHT_COORDINATE
            )
        }

        matrices.pop()
    }

    vertexConsumers.drawCurrentLayer()
}