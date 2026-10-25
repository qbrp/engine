package org.lain.engine.client.render.ui

import foundry.imgui.api.ImGuiMC
import imgui.ImGui
import org.lain.engine.util.math.Vec2

class OptionsWindow : ImGuiWindow {
    override val title: String
        get() = "Настройки Engine"
    override val size: Vec2
        get() {
            val io = ImGui.getIO()
            return Vec2(io.displaySizeX, io.displaySizeY)
        }

    override fun render() {}
}