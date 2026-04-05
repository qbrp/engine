package org.lain.engine.client.render

import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.mc.render.InteractionProgressionRenderState
import org.lain.engine.client.mc.render.NarrationMessageRenderState
import org.lain.engine.player.Narration
import org.lain.cyberia.ecs.require

class ScreenRenderer(private val client: EngineClient) {
    private val window = client.window
    private var ticks = 0

    var hudHidden = false
    var isFirstPerson = false
    val littleNotificationsRenderer = LittleNotificationsRenderManager(window, client.ui)
    val narrations = mutableListOf<NarrationMessageRenderState>()
    val interactionProgression = InteractionProgressionRenderState(0f)
    var chatOpen = false
//    val consoleRenderState = ConsoleRenderState(
//        MutableVec2(window.widthDp - 200, 0f),
//        200,
//        mutableListOf()
//    )

    fun renderScreen(painter: Painter) {
        val gameSession = client.gameSession
        if (!hudHidden && gameSession != null) {
            littleNotificationsRenderer.update(painter.tickDelta)

            val narrationMessages = gameSession.mainPlayer.require<Narration>()
            narrationMessages.messages.forEach { message ->
                if (narrations.none { message.id == it.id }) {
                    narrations += NarrationMessageRenderState(message.id)
                    if (message.kick) client.audioManager.playKickSound()
                }
            }
            narrations.removeIf { narrationMessages.get(it.id) == null }
        }
    }

    fun setupGameSession(gameSession: GameSession) {
        client.ui.addFragment { MovementBar(gameSession) }
    }

    fun invalidate() {
        littleNotificationsRenderer.invalidate()
    }

    fun tick() {
        ticks++
        littleNotificationsRenderer.tick()
    }
}