package org.lain.engine.client.render.ui

import imgui.ImGui
import imgui.type.ImBoolean

interface ImGuiWindow {
    val title: String
    fun render()
    fun onClose() {}
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
        windows.toList().forEachIndexed { index, _ -> closeWindow(index) }
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
        close.asReversed().forEach { closeWindow(it) }
    }

    private fun closeWindow(index: Int) {
        windows.removeAt(index).window.onClose()
    }
}

object ImGuiUtil {
    fun child(name: String, builder: () -> Unit) {
        ImGui.beginChild(name)
        builder()
        ImGui.endChild()
    }
    fun group(builder: () -> Unit) {
        ImGui.beginGroup()
        builder()
        ImGui.endGroup()
    }
}