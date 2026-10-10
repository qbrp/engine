package org.lain.engine.client.render.ui

import imgui.ImGui
import net.minecraft.util.CommonColors
import org.lain.engine.client.script.EntityInspection
import org.lain.engine.script.dev.ComponentInspectionResult
import org.lain.engine.script.dev.InspectionValue
import org.lain.engine.script.dev.InspectionObject
import org.lain.engine.script.dev.InspectionPrimitive
import org.lain.engine.util.Color

class EntityInspectorWindow(
    val inspection: EntityInspection,
    val entityName: String,
) : ImGuiWindow {
    private var expandAllRequested = false
    private val openNodes = mutableSetOf<List<String>>()

    override val title: String = "Инспектор сущности $entityName"

    override fun onClose() {
        inspection.stop()
    }

    override fun render() {
        val data = inspection.data ?: run {
            ImGui.text("Ожидание данных с сервера...")
            return
        }
        ImGuiUtil.group {
            ImGui.text("Идентификатор: ${inspection.persistentId}")
            ImGui.text("Компоненты: ${data.components.count()}")
            ImGui.text("Объекты: ${data.debugObjects.count()}")
            val rateValue = intArrayOf(inspection.rate)
            if (ImGui.sliderInt("Частота обновления", rateValue, 1, 40)) {
                inspection.updateRate(rateValue[0])
            }
        }
        ImGui.separator()
        if (ImGui.button("Расширить всё")) {
            expandAllRequested = true
        }
        ImGuiUtil.child("Компоненты") {
            data.components.forEach { component ->
                val nodePath = listOf("component", component.type)
                val expandComponent = expandAllRequested
                if (expandComponent) {
                    openNodes += nodePath
                }
                ImGui.setNextItemOpen(nodePath in openNodes)
                val componentOpen = ImGui.treeNode(nodeId(nodePath), component.type)
                val expandComponentDescendants = expandComponent || updateNodeState(nodePath, componentOpen)
                if (componentOpen) {
                    val componentData = component.data
                    if (componentData is ComponentInspectionResult.Error) {
                        ImGui.text("Не удалось получить данные")
                        ImGui.textWrapped(componentData.message)
                    } else if (componentData is ComponentInspectionResult.Value) {
                        when(val entry = componentData.entry) {
                            InspectionValue.Null -> { ImGui.textColored(Color.GRAY.integer, "null") }
                            is InspectionValue.Primitive -> entry.render("")
                            is InspectionValue.Reference -> {
                                val componentObjectData = data.debugObjects[entry.id]
                                if (componentObjectData != null) {
                                    if (!componentObjectData.isEmpty) {
                                        componentObjectData.render(
                                            setOf(entry.id),
                                            nodePath,
                                            expandComponentDescendants
                                        )
                                    }
                                } else {
                                    ImGui.textColored(Color.RED.integer, "Данные отсутствуют")
                                }
                            }
                        }
                    }
                    ImGui.treePop()
                }
            }
        }
        expandAllRequested = false
    }

    private fun treeNode(
        key: String,
        index: Int,
        ancestors: Set<Int>,
        nodePath: List<String>,
        expandDescendants: Boolean
    ) {
        if (index in ancestors) {
            ImGui.textColored(Color.GRAY.integer, "$key: cyclic reference $index")
            return
        }
        val componentData = inspection.data?.debugObjects[index]
        val expandNode = expandAllRequested || expandDescendants
        if (expandNode) {
            openNodes += nodePath
        }
        val isOpen = nodePath in openNodes
        val label = if (isOpen) {
            key
        } else {
            componentData?.stringRepresentation()?.let { "$key ($it)" } ?: key
        }
        ImGui.setNextItemOpen(isOpen)
        val nodeOpen = ImGui.treeNode(nodeId(nodePath), label)
        val expandNodeDescendants = expandNode || updateNodeState(nodePath, nodeOpen)
        if (nodeOpen) {
            if (componentData != null) {
                if (!componentData.isEmpty) {
                    componentData.render(ancestors + index, nodePath, expandNodeDescendants)
                }
            } else {
                ImGui.textColored(Color.RED.integer, "Данные отсутствуют")
            }
            ImGui.treePop()
        }
    }

    private fun updateNodeState(nodePath: List<String>, isOpen: Boolean): Boolean {
        if (!ImGui.isItemToggledOpen()) return false
        val recursive = ImGui.getIO().keyShift
        if (isOpen) {
            openNodes += nodePath
        } else {
            openNodes -= nodePath
            if (recursive) {
                openNodes.removeAll { path ->
                    path.size > nodePath.size && path.take(nodePath.size) == nodePath
                }
            }
        }
        return recursive && isOpen
    }

    private fun nodeId(nodePath: List<String>): String {
        return nodePath.joinToString(separator = "/", prefix = "inspection-node-") { segment ->
            "${segment.length}:$segment"
        }
    }

    fun InspectionObject.stringRepresentation(): String? {
        return when(this) {
            is InspectionObject.Collection -> values.joinToString { it.stringRepresentation() }
            is InspectionObject.PlayerPhysics -> null
            is InspectionObject.Table -> values.entries.joinToString { "[${it.key}: ${it.value.stringRepresentation()}]" }
        }
    }

    fun InspectionValue.stringRepresentation(): String {
        return when (this) {
            InspectionValue.Null -> "null"
            is InspectionValue.Primitive -> primitive.stringRepresentation()
            is InspectionValue.Reference -> "reference $id"
        }
    }

    fun InspectionPrimitive.stringRepresentation(): String {
        return when(this) {
            is InspectionPrimitive.Bool -> bool.toString()
            is InspectionPrimitive.Double -> string
            is InspectionPrimitive.Enum -> string
            is InspectionPrimitive.Int -> string
            is InspectionPrimitive.Uneditable -> string
            is InspectionPrimitive.Str -> string
            is InspectionPrimitive.Uuid -> string
            is InspectionPrimitive.Id -> id.full
            is InspectionPrimitive.Char -> char.toString()
        }
    }

    private fun InspectionObject.render(
        ancestors: Set<Int>,
        nodePath: List<String>,
        expandDescendants: Boolean
    ) {
        if (isEmpty) {
            ImGui.textColored(CommonColors.GRAY, "empty")
        }
        when(this) {
            is InspectionObject.Collection -> {
                values.forEachIndexed { index, value ->
                    value.render(
                        index.toString(),
                        ancestors,
                        nodePath + "index:$index",
                        expandDescendants
                    )
                }
            }
            is InspectionObject.Table -> {
                values.forEach { (key, entry) ->
                    entry.render(key, ancestors, nodePath + "key:$key", expandDescendants)
                }
            }

            is InspectionObject.PlayerPhysics -> {
                ImGui.checkbox("noClip", noClip)
            }
        }
    }

    private fun InspectionValue.render(
        key: String,
        ancestors: Set<Int> = emptySet(),
        nodePath: List<String> = emptyList(),
        expandDescendants: Boolean = false
    ) {
        when(this) {
            InspectionValue.Null -> {
                ImGui.text("$key:")
                ImGui.sameLine()
                ImGui.textColored(CommonColors.GRAY, "null")
            }
            is InspectionValue.Primitive -> when(val primitive = primitive) {
                is InspectionPrimitive.Bool -> ImGui.checkbox(key, primitive.bool)
                is InspectionPrimitive.Double -> ImGui.text("$key: " + primitive.string)
                is InspectionPrimitive.Enum -> ImGui.text("$key: " + primitive.string)
                is InspectionPrimitive.Int -> ImGui.text("$key: " + primitive.string)
                is InspectionPrimitive.Uneditable -> ImGui.text("$key: " + primitive.string)
                is InspectionPrimitive.Str -> ImGui.text("$key: " + '"' + primitive.string + '"')
                is InspectionPrimitive.Uuid -> ImGui.text("$key: " + primitive.string)
                is InspectionPrimitive.Id -> ImGui.text("$key: " + primitive.id.full)
                is InspectionPrimitive.Char -> ImGui.text("$key: " + "'" + primitive.char + "'")
            }
            is InspectionValue.Reference -> {
                treeNode(key, id, ancestors, nodePath, expandDescendants)
            }
        }
    }
}
