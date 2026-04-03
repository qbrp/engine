package org.lain.engine.client.chat

import org.lain.engine.client.mc.render.world.LabelRenderState
import org.lain.engine.client.render.FontRenderer
import org.lain.engine.client.util.EngineOptions
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.canSee
import org.lain.engine.player.eyePos
import org.lain.engine.util.math.MutableVec3
import org.lain.engine.util.text.EngineText
import org.lain.engine.util.text.toMinecraft
import org.lain.engine.world.VoxelPos
import org.lain.engine.world.pos
import kotlin.math.pow
import kotlin.math.sin

data class ChatBubble(
    val lines: MutableList<LabelRenderState.Line>,
    val height: Float,
    val player: EnginePlayer,
    var offsetY: Float,
    val pos: MutableVec3,
    val targetPos: MutableVec3,
    var opacity: Float,
    val expiration: Int,
    var lifetime: Float,
    var remove: Boolean = false,
    var canSee: Boolean = true,
    var tick: Int = 0,
    var squaredDistanceToCamera: Float = 0f,
)

class ChatBubbleList(
    private val options: EngineOptions,
    private val fontRenderer: FontRenderer
) {
    private val _bubbles = mutableListOf<ChatBubble>()
    val bubbles: List<ChatBubble>
        get() = _bubbles

    fun setChatBubble(player: EnginePlayer, text: EngineText) {
        val lines = fontRenderer.breakTextByLines(text, options.chatBubbleLineWidth.toFloat())
        val height = lines.count() * fontRenderer.fontHeight

        // Все прошлые чат-баблы сдвигаем вверх
        _bubbles
            .filter { it.player.id == player.id }
            .forEach { it.offsetY += (height + 2) * options.chatBubbleScale}

        _bubbles.add(
            ChatBubble(
                lines
                    .map { LabelRenderState.Line(it.toMinecraft(), fontRenderer.getWidth(it).toInt()) }
                    .reversed()
                    .toMutableList(),
                height,
                player,
                0f,
                MutableVec3(player.eyePos),
                MutableVec3(player.pos),
                0f,
                options.chatBubbleLifeTime,
                0f
            )
        )
    }

    fun tick(mainPlayer: EnginePlayer) {
        val players = mutableMapOf<PlayerId, Boolean>()
        bubbles.forEach { bubble ->
            if (bubble.tick++ % 20 == 0) {
                val canSeeAuthor = players.computeIfAbsent(bubble.player.id) { mainPlayer.canSee(VoxelPos(bubble.player.pos), true) }
                if (!canSeeAuthor) updateChatBubbleCanSee(bubble)
            }
        }
    }

    fun cleanup() {
        _bubbles.removeIf { it.remove }
    }
}

private const val LIFTING_Y = 3
private const val FADE_OUT_TIME = 3 * 20 // ticks

fun updateChatBubble(bubble: ChatBubble, dt: Float, height: Float) {
    val lifetime = bubble.lifetime
    val fadeout = lifetime > bubble.expiration

    val playerPos = bubble.player.pos
    val t = (lifetime / bubble.expiration).coerceIn(0f, 1f)
    val t2 = ((lifetime - bubble.expiration) / FADE_OUT_TIME)
        .coerceIn(0f, 1f)
    val lift1 = t * LIFTING_Y * 0.15f
    val lift2 = sin(t2 * (Math.PI / 2)).toFloat() * LIFTING_Y * 0.85f
    val lift = lift1 + lift2

    if (bubble.canSee) {
        bubble.targetPos.set(
            playerPos.x,
            playerPos.y + height + lift + bubble.offsetY,
            playerPos.z
        )
    }
    bubble.pos.mutateLerp(bubble.targetPos, 1f - 0.8f.pow(dt))
    val opacity = 1 - sin(t2 * (Math.PI / 2)).toFloat()
    bubble.opacity = opacity
    bubble.lifetime += dt

    if (opacity <= 0.03f && fadeout) {
        bubble.remove = true
    }
}

fun updateChatBubbleCanSee(bubble: ChatBubble) {
    bubble.canSee = bubble.player.canSee(VoxelPos(bubble.pos), true)
}