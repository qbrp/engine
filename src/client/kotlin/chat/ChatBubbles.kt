package org.lain.engine.client.chat

import org.lain.engine.client.render.FontRenderer
import org.lain.engine.client.util.EngineOptions
import org.lain.engine.player.Player
import org.lain.engine.util.MutableVec3
import org.lain.engine.util.lerp
import org.lain.engine.util.text.EngineOrderedText
import org.lain.engine.util.text.EngineText
import org.lain.engine.world.pos
import kotlin.math.pow

data class ChatBubble(
    val lines: List<Line>,
    val height: Float,
    val player: Player,
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

    fun setChatBubble(player: Player, text: String) {
        val lines = fontRenderer.breakTextByLines(
            EngineText(content = text),
            options.chatBubbleLineWidth.toFloat()
        )
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

fun updateChatBubble(bubble: ChatBubble, dt: Float, height: Float) {
    val playerPos = bubble.player.pos
    val alpha = 1f - 0.8f.pow(dt)
    bubble.pos.mutateLerp(
        playerPos.x,
        playerPos.y + height + bubble.offsetY,
        playerPos.z,
        alpha
    )
    val opacity = bubble.opacity
    val lifetime = bubble.lifetime
    val fadeout = lifetime > bubble.expiration

    val targetOpacity = if (fadeout) 0f else 1f
    bubble.opacity = lerp(opacity, targetOpacity, alpha)
    bubble.lifetime += dt

    if (opacity <= 0.01f && fadeout) {
        bubble.remove = true
    }
}