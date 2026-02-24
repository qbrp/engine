package org.lain.engine.client.render

import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.mc.render.NarrationMessageRenderState
import org.lain.engine.player.Narration
import org.lain.engine.util.require

class ScreenRenderer(private val client: EngineClient) {
    private val window = client.window
    private var ticks = 0

    var hudHidden = false
    var isFirstPerson = false
    val littleNotificationsRenderer = LittleNotificationsRenderManager(window, client.ui)
    var narrations = mutableListOf<NarrationMessageRenderState>()

    fun renderScreen(painter: Painter) {
        val gameSession = client.gameSession
        if (!hudHidden && gameSession != null) {
            littleNotificationsRenderer.update(painter.tickDelta)

            val narrationMessages = gameSession.mainPlayer.require<Narration>().messages
            narrationMessages.forEachIndexed { index, message ->
                if (narrations.none { narrationMessages[it.index].content == message.content }) {
                    narrations += NarrationMessageRenderState(index, 0f)
                }
            }
            narrations.removeIf { it.time >= narrationMessages[it.index].content.duration }
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