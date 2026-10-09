package org.lain.engine.client.render.ui

import imgui.ImGui
import org.lain.engine.client.script.EntityInspection
import org.lain.engine.script.EntityDebugData

class EntityInspectorWindow(
    val inspection: EntityInspection,
) : ImGuiWindow {
    override val title: String = "Инспектор сущности ${inspection.entity} (${inspection.persistentId})"

    override fun render() {
        val data = inspection.data ?: run {
            ImGui.text("Ожидание данных с сервера...")
            return
        }
        ImGui.text("Компоненты: ${data.components.count()}")
    }
}