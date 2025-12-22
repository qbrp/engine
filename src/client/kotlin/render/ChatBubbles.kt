package org.lain.engine.client.render

import org.lain.engine.client.util.EngineOptions
import org.lain.engine.client.chat.EngineChatMessage
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerId
import org.lain.engine.util.MutableVec3
import org.lain.engine.util.lerp
import org.lain.engine.world.pos
import kotlin.math.pow

data class ChatBubble(
    val message: EngineChatMessage,
    var pos: MutableVec3,
    var opacity: Int,
    val expiration: Int,
    var lifetime: Float
)

class ChatBubbleManager(private val options: EngineOptions) {
    private val bubbles = mutableMapOf<PlayerId, ChatBubble>()
    private val chatBubbleHeight
        get() = options.chatBubbleHeight

    fun getChatBubble(player: Player) = bubbles[player.id]

    fun setChatBubble(player: Player, message: EngineChatMessage) {
        bubbles[player.id] = ChatBubble(
            message,
            MutableVec3(player.pos),
            0,
            options.chatBubbleLifeTime.get(),
            0f
        )
    }

    fun update(bubble: ChatBubble, player: Player, dt: Float) {
        val playerPos = player.pos
        val alpha = 1f - 0.8f.pow(dt)
        bubble.pos.mutateLerp(
            playerPos.x,
            playerPos.y + chatBubbleHeight.get(),
            playerPos.z,
            0.2f
        )
        val opacity = bubble.opacity
        val lifetime = bubble.lifetime
        val fadeout = lifetime > bubble.expiration

        val targetOpacity = if (fadeout) {
            0f
        } else {
            255f
        }

        bubble.opacity = lerp(opacity.toFloat(), targetOpacity, alpha).toInt()
        bubble.lifetime += dt

        if (opacity <= 0f && fadeout) {
            bubbles.remove(player.id)
        }
    }
}