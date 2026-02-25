package org.lain.engine.client.chat

import org.lain.engine.client.render.FontRenderer
import org.lain.engine.client.util.EngineOptions
import org.lain.engine.player.EnginePlayer
import org.lain.engine.util.math.MutableVec3
import org.lain.engine.util.text.EngineOrderedText
import org.lain.engine.util.text.EngineText
import org.lain.engine.world.pos
import kotlin.math.pow
import kotlin.math.sin

data class ChatBubble(
    val lines: List<Line>,
    val height: Float,
    val player: EnginePlayer,
    var offsetY: Float,
    var pos: MutableVec3,
    var opacity: Float,
    val expiration: Int,
    var lifetime: Float,
    var remove: Boolean = false
) {
    data class Line(
        val text: EngineOrderedText,
        val width: Float
    )
}

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
                    .map { ChatBubble.Line(it, fontRenderer.getWidth(it)) }
                    .reversed(),
                height,
                player,
                0f,
                MutableVec3(player.pos),
                0f,
                options.chatBubbleLifeTime,
                0f
            )
        )
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
    val t2 = (lifetime - bubble.expiration).coerceIn(0f, 1f) / FADE_OUT_TIME
    val lift1 = t * LIFTING_Y * 0.15f
    val lift2 = sin(t2 * (Math.PI / 2)).toFloat() * LIFTING_Y * 0.85f
    val lift = lift1 + lift2
    bubble.pos.mutateLerp(
        playerPos.x,
        playerPos.y + height + lift + bubble.offsetY,
        playerPos.z,
        1f - 0.8f.pow(dt)
    )
    val opacity = 1 - sin(t2 * (Math.PI / 2)).toFloat()
    bubble.opacity = opacity
    bubble.lifetime += dt

    if (opacity <= 0.03f && fadeout) {
        bubble.remove = true
    }
}