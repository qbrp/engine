package org.lain.engine.client.mc.render.world

import net.minecraft.client.render.Camera
import net.minecraft.client.render.LightmapTextureManager
import org.lain.engine.client.chat.ChatBubble
import org.lain.engine.client.chat.updateChatBubble
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.mc.engine
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.then

context(ctx: ImmediateWorldRenderContext)
fun renderChatBubbles(
    camera: Camera,
    easingDistance: Float,
    scale: Float,
    height: Float,
    backgroundOpacity: Float,
    bubbles: List<ChatBubble>,
    ignoreLightLevel: Boolean,
    dt: Float,
) {
    val client = MinecraftClient
    val entityTable by injectEntityTable()
    if (client.player == null || client.world == null) {
        return
    }

    for (bubble in bubbles) {
        updateChatBubble(bubble, dt, height)
        bubble.squaredDistanceToCamera = bubble.pos.squaredDistanceTo(camera.pos.engine())
        val easing = { bubble.canSee }.then { LabelEasing(bubble.squaredDistanceToCamera, easingDistance*easingDistance) }
        val player = entityTable.client.getEntity(bubble.player)
        val bubblePos = bubble.pos
        val alpha = bubble.opacity

        renderLabel(
            camera,
            LabelRenderState(bubblePos, alpha, bubble.lines, scale),
            backgroundOpacity,
            if (!ignoreLightLevel && player != null) {
                LightmapTextureManager.applyEmission(
                    client.entityRenderDispatcher.getLight(player, client.renderTickCounter.getTickProgress(true)
                    ),
                    2
                )
            } else {
                LightmapTextureManager.MAX_LIGHT_COORDINATE
            },
            easing
        )
    }
}