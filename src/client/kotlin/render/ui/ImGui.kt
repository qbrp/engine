package org.lain.engine.client.render.ui

import imgui.ImGui
import imgui.extension.imguizmo.ImGuizmo
import imgui.type.ImBoolean

interface ImGuiWindow {
    val title: String
    fun render()
    fun postRender() {}
    fun onClose() {}
}

class ImGuiManager {
    data class Slot(
        val window: ImGuiWindow,
        val opened: ImBoolean
    )

    private val windows = mutableMapOf<ImGuiWindow, Slot>()

    fun addWindow(window: ImGuiWindow) {
        windows[window] = Slot(window, ImBoolean(true))
    }

    fun close(window: ImGuiWindow) {
        windows.remove(window)?.window?.onClose()
    }

    fun closeAll() {
        windows.values.forEach { it.window.onClose() }
        windows.clear()
    }

    fun render() {
        ImGuizmo.beginFrame()
        val close = mutableListOf<ImGuiWindow>()
        windows.values.forEach { (window, opened) ->
            val visible = ImGui.begin(window.title, opened)
            if (!opened.get()) {
                close.add(window)
            } else if (visible) {
                window.render()
            }
            ImGui.end()
        }
        close.forEach { close(it) }
    }

    fun postRender() {
        windows.values.forEach { it.window.postRender() }
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
