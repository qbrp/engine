package org.lain.engine.client.render.ui

import net.minecraft.client.gui.screens.Screen
import org.lain.engine.client.EngineClient
import org.lain.engine.client.chat.LiteralSystemMessage
import org.lain.engine.mc.literalText
import org.lain.engine.script.EntityDebugData

class EntityDebugScreen(private val client: EngineClient) : Screen(literalText("Entity debug")) {
    override fun init() {
        client.gameSession?.let { gameSession ->
            gameSession.chatManager.addMessage(
                LiteralSystemMessage(gameSession, "Экран отладки сущностей временно не поддерживатся. WIP...")
            )
        }
    }

    fun applyEntityDebugData(data: EntityDebugData.Dto) {
        //TODO
    }

    override fun onClose() {
        client.handler.onEntityDebugViewStop()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean {
        return false
    }
}