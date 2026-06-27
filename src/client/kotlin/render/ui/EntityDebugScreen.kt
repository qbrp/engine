package org.lain.engine.client.render.ui

import kotlinx.serialization.json.Json
import net.minecraft.client.gui.screens.Screen
import org.lain.engine.client.EngineClient
import org.lain.engine.mc.emptyText
import org.lain.engine.mc.literalText
import org.lain.engine.player.EntityDebugData
import tytoo.grapheneui.api.widget.GrapheneWebViewWidget

class EntityDebugScreen(private val client: EngineClient) : Screen(literalText("Entity debug")) {
    private lateinit var view: GrapheneWebViewWidget

    override fun init() {
        val margin = 8
        view = GrapheneWebViewWidget(
            this,
            margin,
            margin,
            width - margin * 2,
            height - margin * 2,
            emptyText(),
            webPageUrl("debug/entity")
        )
        addRenderableWidget(view)
    }

    fun applyEntityDebugData(data: EntityDebugData) {
        if (!::view.isInitialized) return
        view.bridge().emit("data", Json.encodeToString(data))
    }

    override fun onClose() {
        client.handler.onEntityDebugViewStop()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean {
        return false
    }
}