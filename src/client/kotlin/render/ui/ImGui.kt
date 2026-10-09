package org.lain.engine.client.render.ui

import imgui.ImGui
import imgui.type.ImBoolean

interface ImGuiWindow {
    val title: String
    fun render()
}

class ImGuiManager {
    data class Slot(
        val window: ImGuiWindow,
        val opened: ImBoolean
    )

    private val windows = mutableListOf<Slot>()

    fun addWindow(window: ImGuiWindow) {
        windows += Slot(window, ImBoolean(true))
    }

    fun closeAll() {
        windows.clear()
    }

    fun render() {
        val close = mutableListOf<Int>()
        windows.forEachIndexed { index, (window, opened) ->
            ImGui.begin(window.title, opened)
            if (!opened.get()) {
                close.add(index)
            } else {
                window.render()
            }
            ImGui.end()
        }
        close.forEach { windows.removeAt(it) }
    }
}