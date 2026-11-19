package org.lain.engine.client.render.ui

import imgui.ImGui
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.type.ImBoolean
import org.lain.engine.client.render.ui.property.PropertyEditor
import org.lain.engine.client.script.EntityInspection
import org.lain.engine.script.ComponentInspectionResult
import org.lain.engine.script.EntityInspectionSnapshotDto
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.util.ecs.EngineComponentType
import org.lain.engine.util.ecs.lookupComponentType
import org.lain.engine.util.math.Vec2

class EntityInspectorWindow(
    val inspection: EntityInspection,
    val entityName: String,
    val namespacedStorage: NamespacedStorageAccess,
) : ImGuiWindow {
    private var abortReason: String? = null
    private val expandAll: ImBoolean = ImBoolean(false)
    private val shortNumberFormatting: ImBoolean = ImBoolean(true)
    private val shortComponentNamesFormatting: ImBoolean = ImBoolean(true)
    private val showTypes: ImBoolean = ImBoolean(true)

    override val title: String = "Инспектор сущности $entityName"
    override val size: Vec2
        get() = Vec2(1650f, 800f)

    private sealed interface Item
    data class Error(val message: String) : Item
    data class Component(val editor: PropertyEditor) : Item

    private var components = mutableMapOf<String, Item>()
    private val editableComponents get() = components.values.filterIsInstance<Component>()

    override fun onClose() {
        inspection.stop()
    }

    fun abort(reason: String) {
        abortReason = reason
        return
    }

    fun onDataUpdated(data: EntityInspectionSnapshotDto) {
        data.components.sortedBy { it.type }.forEach { component ->
            val result = component.data
            val type = component.type

            when (result) {
                is ComponentInspectionResult.Error -> components[type] = Error(result.message)
                is ComponentInspectionResult.Value -> {
                    val item = (components[type] as? Component)
                        ?: run {
                            Component(
                                PropertyEditor(
                                    type,
                                    result.root,
                                    result.tree,
                                    shortNumberFormatting,
                                    showTypes,
                                    expandAll.get()
                                ) { property, edit ->
                                    inspection.editValue(type, property.path, edit)
                                }
                            )
                                .also { components[type] = it }
                        }
                    item.editor.update(result.root, result.tree)
                }
            }
        }
    }

    override fun render() {
        if (abortReason != null) {
            ImGui.text(abortReason)
            return
        }
        val data = inspection.data ?: run {
            ImGui.text("Ожидание данных с сервера...")
            return
        }
        ImGuiUtil.group {
            ImGui.text("Идентификатор: ${inspection.persistentId}")
            ImGui.text("Компоненты: ${data.components.count()}")
            val objectCount =
                data.components.sumOf { (it.data as? ComponentInspectionResult.Value)?.tree?.compounds?.size ?: 0 }
            ImGui.text("Объекты: $objectCount")
            if (ImGui.checkbox("Расширить всё", expandAll)) {
                editableComponents.forEach { it.editor.expandAll = expandAll.get() }
            }
            ImGui.checkbox("Показывать типы", showTypes)
            ImGui.checkbox("Сокращённые названия компонентов", shortComponentNamesFormatting)
            ImGui.checkbox("Округлять нецелые числа", shortNumberFormatting)
            val rateValue = intArrayOf(inspection.rate)
            if (ImGui.sliderInt("Частота обновления", rateValue, 1, 40)) {
                inspection.updateRate(rateValue[0])
            }
        }
        ImGui.separator()
        ImGui.textWrapped("Изменение данных некоторых компонентов может повлечь за собой неккоректную работу Engine")
        ImGui.separator()
        ImGuiUtil.child("Компоненты") {
            val flags = ImGuiTableFlags.Resizable or ImGuiTableFlags.RowBg or ImGuiTableFlags.BordersInnerV or ImGuiTableFlags.SizingStretchProp
            val columnsCount = if (showTypes.get()) 3 else 2

            if (ImGui.beginTable("properties", columnsCount, flags)) {
                ImGui.tableSetupColumn("Свойство", ImGuiTableColumnFlags.WidthStretch, 0.25f)
                if (showTypes.get()) ImGui.tableSetupColumn("Тип", ImGuiTableColumnFlags.WidthStretch, 0.15f)
                ImGui.tableSetupColumn("Значение", ImGuiTableColumnFlags.WidthStretch, 0.55f)
                ImGui.tableHeadersRow()

                components.forEach { (type, item) ->
                    val componentType = lookupComponentType(type, namespacedStorage.get())
                    val meta = when (componentType) {
                        is EngineComponentType<*> -> componentType.meta
                        is ScriptComponentType -> componentType.meta
                        else -> null
                    }

                    val shortNames = shortComponentNamesFormatting.get()
                    val shortLabel = type.substringAfterLast("/")
                        .takeIf { shortNames }

                    val label = StringBuilder(shortLabel ?: type)
                    if (item is Error) label.append(" [ERROR]")
                    if (meta?.savable == true && !shortNames) label.append(" [S]")

                    when (item) {
                        is Component -> item.editor.renderRoot(label.toString()) {
                            if (meta?.networking == true) {
                                ImGui.sameLine()
                                if (ImGui.smallButton("markDirty##${type}")) {
                                    inspection.markDirty(type)
                                }
                            }
                        }

                        is Error -> {
                            ImGui.tableNextRow()
                            ImGui.tableNextColumn()
                            val opened = ImGui.treeNode("inspection-error-$type", label.toString().replace("%", "%%"))
                            if (showTypes.get()) {
                                ImGui.tableNextColumn()
                                ImGui.textDisabled("ошибка")
                            }
                            ImGui.tableNextColumn()
                            ImGui.textDisabled("Не удалось получить данные")
                            if (opened) {
                                ImGui.tableNextRow()
                                ImGui.tableNextColumn()
                                ImGui.textUnformatted("Сообщение")
                                ImGui.tableSetColumnIndex(columnsCount)
                                ImGui.textWrapped(item.message.replace("%", "%%"))
                                ImGui.treePop()
                            }
                        }
                    }
                }
                ImGui.endTable()
            }
        }
    }
}
