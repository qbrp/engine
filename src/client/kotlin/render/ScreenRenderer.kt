package org.lain.engine.client.render

import org.lain.engine.client.EngineClient

class ScreenRenderer(
    private val window: Window,
    private val fontRenderer: FontRenderer,
    private val camera: Camera,
    private val client: EngineClient
) {
    private var ticks = 0

    var hudHidden = false
    val littleNotificationsRenderer = LittleNotificationsRenderManager(fontRenderer, window)
    val movementStatusRenderer = MovementStatusRenderer(window, client)
    val chatBubbleRenderer = ChatBubbleManager(client.options)

    fun renderScreen(painter: Painter, isFirstPerson: Boolean) {
        val gameSession = client.gameSession
        if (!hudHidden && gameSession != null) {
            val movementManager = gameSession.movementManager
            littleNotificationsRenderer.render(painter)
            if (isFirstPerson) {
                movementStatusRenderer.render(
                    painter,
                    movementManager.intention,
                    movementManager.stamina,
                    painter.tickDelta
                )
             }
        }
    }

    fun invalidate() {
        littleNotificationsRenderer.invalidate()
    }

    fun tick() {
        ticks++
        littleNotificationsRenderer.tick()
    }
}