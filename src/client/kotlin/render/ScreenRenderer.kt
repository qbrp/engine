package org.lain.engine.client.render

import org.lain.cyberia.ecs.iterate
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.render.ui.InteractionProgressionRenderState
import org.lain.engine.client.render.ui.hud.MovementStatusRenderState
import org.lain.engine.client.render.ui.hud.NarrationMessageRenderState
import org.lain.engine.client.render.ui.hud.LittleNotificationsRenderManager
import org.lain.engine.client.render.world.BlockHintInspectionRenderState
import org.lain.engine.player.NarrationEvent

class ScreenRenderer(private val client: EngineClient) {
    private val window = client.window
    private var ticks = 0

    var hudHidden = false
    var isFirstPerson = false
    val littleNotificationsRenderer = LittleNotificationsRenderManager(window)
    val narrations = mutableListOf<NarrationMessageRenderState>()
    val interactionProgression = InteractionProgressionRenderState(0f)
    val movementStatus = MovementStatusRenderState()
    var chatOpen = false
    var blockHintInspectionRenderState: BlockHintInspectionRenderState? = null

    fun tick(gameSession: GameSession) {
        ticks++
        gameSession.world.iterate<NarrationEvent> { _, (playerId, narration) ->
            if (gameSession.mainPlayer.id != playerId) return@iterate
            val existingNarration = narrations.find { it.message.text == narration.text }
            if (existingNarration != null) {
                existingNarration.time = (existingNarration.time - narration.duration).coerceAtLeast(0f)
            } else {
                narrations += NarrationMessageRenderState(narration)
            }
            if (narration.kick) client.audioManager.playKickSound()
        }
        narrations.removeIf { it.time > it.message.duration }
        littleNotificationsRenderer.tick()
    }

    fun renderScreen(tickDelta: Float) {
        if (!hudHidden) {
            littleNotificationsRenderer.update(tickDelta)
        }
    }

    fun invalidate() {
        littleNotificationsRenderer.invalidate()
        narrations.clear()
    }
}
