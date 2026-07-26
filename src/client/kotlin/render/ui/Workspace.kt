package org.lain.engine.client.render.ui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import org.lain.engine.client.GameSession
import org.lain.engine.client.script.ClientCallbacks
import org.lain.engine.client.script.ClientScriptContext
import org.lain.engine.mc.literalText
import org.lain.engine.script.ScriptContext
import tytoo.grapheneui.api.screen.GrapheneScreens
import tytoo.grapheneui.api.widget.GrapheneWebViewWidget

class Workspace(
    private val gameSession: GameSession,
    val state: SavedState?
) : Screen(literalText("Рабочий стол")) {
    private var firstTimeInitialized = false
    private var drag: DragState? = null
    val windows = state?.windows?.toMutableMap() ?: linkedMapOf()

    fun addWindow(id: String, uri: String, windowWidth: Int, windowHeight: Int): Window {
        windows[id]?.let { return it }

        return Window(
            id,
            width / 2 - windowWidth / 2,
            height / 2 - windowHeight / 2,
            windowWidth, windowHeight,
            uri
        ).also {
            addRenderableWidget(it)
            windows[id] = it
        }
    }

    private fun closeWindow(window: Window) {
        windows.remove(window.id)
        removeWidget(window)
    }

    override fun init() {
        GrapheneScreens.setWebViewAutoCloseEnabled(this, false)
        if (!firstTimeInitialized) {
            if (state == null) { // если открыт в первый раз
                addWindow(
                    "engine/hello",
                    builtinWebPageUrl("workspace/hello"),
                    120,
                    70
                )
            }
            firstTimeInitialized = true
        }
        windows.values.forEach {
            if (!children().contains(it)) {
                addRenderableWidget(it)
                it.bridge().onEvent("window:close") { _, _ ->
                    closeWindow(it)
                }
            }
        }
        gameSession.callbacks.of(ClientCallbacks.WORKSPACE_OPEN)?.execute(ClientScriptContext.WorkspaceOpen(this))
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, d: Double, e: Double): Boolean {
        val drag = drag
        return if (drag != null) {
            val mouseX = mouseButtonEvent.x.toInt()
            val mouseY = mouseButtonEvent.y.toInt()
            drag.window.x = mouseX - drag.relativeX
            drag.window.y = mouseY - drag.relativeY
            true
        } else {
            super.mouseDragged(mouseButtonEvent, d, e)
        }
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        val mouseX = mouseButtonEvent.x.toInt()
        val mouseY = mouseButtonEvent.y.toInt()
        val draggedWindow = windows.values.lastOrNull { window ->
            mouseX in window.x..(window.x + window.width) && mouseY in window.y..(window.y + window.height)
        }
        if (draggedWindow == null) {
            return super.mouseClicked(mouseButtonEvent, bl)
        }

        if (draggedWindow.isTitlebarRightSide(mouseX, mouseY)) {
            return super.mouseClicked(mouseButtonEvent, bl)
        }

        if (!draggedWindow.isTitlebar(mouseY)) {
            return super.mouseClicked(mouseButtonEvent, bl)
        }

        drag = DragState(
            draggedWindow,
            mouseX - draggedWindow.x,
            mouseY - draggedWindow.y,
        )
        return true
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        return if (drag != null) {
            drag = null
            true
        } else {
            super.mouseReleased(mouseButtonEvent)
        }
    }

    override fun onClose() {
        super.onClose()
        gameSession.workspaceSavedState = SavedState(windows.toMap())
    }

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(guiGraphics: GuiGraphics, i: Int, j: Int, f: Float) {}

    inner class Window(
        val id: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        url: String
    ) : GrapheneWebViewWidget(this, x, y, width, height, literalText("Window"), url) {
        init {
            bridge().onEvent("window:close") { _, _ ->
                closeWindow(this)
            }
        }

        fun isTitlebar(mouseY: Int): Boolean {
            return mouseY in (y + WINDOW_MARGIN)..(y + WINDOW_MARGIN + TITLEBAR_HEIGHT)
        }

        fun isTitlebarRightSide(mouseX: Int, mouseY: Int): Boolean {
            return isTitlebar(mouseY) && mouseX >= x + width - TITLEBAR_RIGHT_RESERVED_WIDTH
        }
    }

    data class SavedState(
        val windows: Map<String, Window>
    )

    data class DragState(
        val window: Window,
        val relativeX: Int,
        val relativeY: Int,
    )

    companion object {
        private const val WINDOW_MARGIN = 4
        private const val TITLEBAR_HEIGHT = 22
        private const val TITLEBAR_RIGHT_RESERVED_WIDTH = 28
    }
}
