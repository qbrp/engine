package org.lain.engine.client.mc

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.RotationAxis
import org.lain.engine.client.render.BLACK
import org.lain.engine.client.render.ChatBubble
import org.lain.engine.client.render.ChatBubbleManager
import org.lain.engine.client.render.WHITE
import org.lain.engine.client.render.withAlpha

fun renderChatBubbles(
    entity: AbstractClientPlayerEntity,
    tickDelta: Float,
    bubble: ChatBubble,
    scale: Float,
    matrices: MatrixStack,
    vertexConsumers: VertexConsumerProvider
) {
    val camera = MinecraftClient.gameRenderer.camera
    val textRenderer = MinecraftClient.textRenderer
    val lines = MinecraftChat.getMessageData(bubble.message)?.brokenChatBubbleLines ?: return
    val light = MinecraftClient.entityRenderDispatcher.getLight(entity, tickDelta)
    val opacity = bubble.opacity
    val pos = bubble.pos

    matrices.push()
    matrices.translate(pos.x, pos.y, pos.z)

    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.yaw))
    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.pitch))

    matrices.scale(-0.025f * scale, -0.025f * scale, 0.025f * scale)

    var i = 0f
    for (line in lines) {
        textRenderer.draw(
            line,
            (-textRenderer.getWidth(line)) / 2f,
            i,
            WHITE.withAlpha(opacity),
            true,
            matrices.peek()?.positionMatrix,
            vertexConsumers,
            TextRenderer.TextLayerType.NORMAL,
            BLACK,
            light
        )
        i += textRenderer.fontHeight
    }
    matrices.pop()
}