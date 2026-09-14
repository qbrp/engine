package org.lain.engine.client.render.world

import net.minecraft.client.Camera
import net.minecraft.client.renderer.LightTexture
import org.lain.engine.client.chat.ChatBubble
import org.lain.engine.client.chat.updateChatBubble
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.mc.ecs.engine
import org.lain.engine.mc.ecs.minecraftEntityNullable
import org.lain.engine.mc.engine
import org.lain.engine.util.then
import kotlin.math.max

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
    if (client.player == null || client.level == null) {
        return
    }

    for (bubble in bubbles) {
        updateChatBubble(bubble, dt, height)
        bubble.squaredDistanceToCamera = bubble.pos.squaredDistanceTo(camera.position.engine())
        val easing = { bubble.canSee }.then { LabelEasing(bubble.squaredDistanceToCamera, easingDistance*easingDistance) }
        val player = bubble.player.minecraftEntityNullable
        val bubblePos = bubble.pos
        val alpha = bubble.opacity

        renderLabel(
            camera,
            LabelRenderState(bubblePos, alpha, bubble.lines, scale),
            backgroundOpacity,
            if (!ignoreLightLevel && player != null) {
                client.entityRenderDispatcher.getPackedLightCoords(
                    player,
                    client.timer.getGameTimeDeltaPartialTick(true)
                ).let { packedLight ->
                    LightTexture.pack(
                        max(LightTexture.block(packedLight), 2),
                        LightTexture.sky(packedLight)
                    )
                }
            } else {
                LightTexture.FULL_BRIGHT
            },
            easing
        )
    }
}
