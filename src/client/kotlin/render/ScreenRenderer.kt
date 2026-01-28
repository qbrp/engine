package org.lain.engine.client.render

import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.mc.BlockHint

class ScreenRenderer(private val client: EngineClient) {
    private val window = client.window
    private var ticks = 0

    var hudHidden = false
    var isFirstPerson = false
    val littleNotificationsRenderer = LittleNotificationsRenderManager(window, client.ui)

    fun renderScreen(painter: Painter) {
        val gameSession = client.gameSession
        if (!hudHidden && gameSession != null) {
            littleNotificationsRenderer.update(painter.tickDelta)
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