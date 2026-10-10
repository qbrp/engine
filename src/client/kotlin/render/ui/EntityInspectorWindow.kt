package org.lain.engine.client.render.ui

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImString
import net.minecraft.util.CommonColors
import org.lain.engine.client.script.EntityInspection
import org.lain.engine.script.EngineId
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.dev.ComponentInspectionResult
import org.lain.engine.script.dev.EntityInspectionSnapshot
import org.lain.engine.script.dev.InspectionValue
import org.lain.engine.script.dev.InspectionObject
import org.lain.engine.script.dev.InspectionPrimitive
import org.lain.engine.util.Color
import org.lain.engine.util.ecs.EngineComponentType
import org.lain.engine.util.ecs.lookupComponentType

class EntityInspectorWindow(
    val inspection: EntityInspection,
    val entityName: String,
    val namespacedStorage: NamespacedStorageAccess,
) : ImGuiWindow {
    private val expandAll = ImBoolean(false)
    private val openNodes = mutableSetOf<List<String>>()
    private val recursiveOpenRequests = mutableSetOf<List<String>>()
    private val editedPrimitives = mutableMapOf<InspectionPrimitiveAddress, PendingEdit>()
    private var abortReason: String? = null

    data class InspectionPrimitiveAddress(val objectId: Int, val key: String)
    data class PendingEdit(val original: InspectionPrimitive, val edited: InspectionPrimitive)

    override val title: String = "Инспектор сущности $entityName"

    override fun onClose() {
        inspection.stop()
    }

    fun abort(reason: String) {
        abortReason = reason
        return
    }

    fun onDataUpdated(data: EntityInspectionSnapshot.Dto) {
        editedPrimitives.entries.removeIf { (address, pending) ->
            val current = (
                data.debugObjects[address.objectId]?.getValue(address.key) as? InspectionValue.Primitive
            )?.primitive

            current == null || current == pending.edited || current != pending.original
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
            ImGui.text("Объекты: ${data.debugObjects.count()}")
            val rateValue = intArrayOf(inspection.rate)
            if (ImGui.sliderInt("Частота обновления", rateValue, 1, 40)) {
                inspection.updateRate(rateValue[0])
            }
        }
        ImGui.separator()
        ImGui.textWrapped("Изменение данных некоторых компонентов может повлечь за собой неккоректную работу Engine")
        ImGui.separator()
        if (ImGui.checkbox("Расширить всё", expandAll)) {
            if (!expandAll.get()) {
                openNodes.clear()
            }
        }
        ImGuiUtil.child("Компоненты") {
            data.components.forEach { component ->
                val nodePath = listOf("component", component.type)
                val expandComponent = expandAll.get() || recursiveOpenRequests.remove(nodePath)
                if (expandComponent) {
                    openNodes += nodePath
                }
                ImGui.setNextItemOpen(nodePath in openNodes)

                val componentData = component.data
                val hasError = componentData is ComponentInspectionResult.Error
                val componentType = lookupComponentType(component.type, namespacedStorage.get())
                val meta = when(componentType) {
                    is EngineComponentType<*> -> componentType.meta
                    is ScriptComponentType -> componentType.meta
                    else -> null
                }

                val label = StringBuilder(component.type)
                if (hasError) {
                    label.append(" [⚠]")
                }
                if (meta?.savable == true) {
                    label.append(" [S]")
                }

                val componentOpen = ImGui.treeNode(nodeId(nodePath), label.toString())
                val expandComponentDescendants = expandComponent || updateNodeState(nodePath, componentOpen)

                if (meta?.networking == true) {
                    ImGui.sameLine()
                    if (ImGui.smallButton("markDirty##${component.type}")) {
                        inspection.markDirty(component.type)
                    }
                }
                if (componentOpen) {
                    if (hasError) {
                        ImGui.text("Не удалось получить данные")
                        ImGui.textWrapped(componentData.message)
                    } else if (componentData is ComponentInspectionResult.Value) {
                        when(val entry = componentData.entry) {
                            InspectionValue.Null -> { ImGui.textColored(Color.GRAY.integer, "null") }
                            is InspectionValue.Primitive -> entry.render(null, "")
                            is InspectionValue.Reference -> {
                                val componentObjectData = data.debugObjects[entry.id]
                                if (componentObjectData != null) {
                                    if (!componentObjectData.isEmpty) {
                                        componentObjectData.render(
                                            entry.id,
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
    }

    private fun treeNode(
        key: String,
        objectId: Int,
        ancestors: Set<Int>,
        nodePath: List<String>,
        expandDescendants: Boolean
    ) {
        if (objectId in ancestors) {
            ImGui.textColored(Color.GRAY.integer, "$key: cyclic reference $objectId")
            return
        }
        val componentData = inspection.data?.debugObjects[objectId]
        val expandNode = expandAll.get() || expandDescendants || recursiveOpenRequests.remove(nodePath)
        if (expandNode) {
            openNodes += nodePath
        }
        val isOpen = nodePath in openNodes
        val label = if (isOpen) {
            key
        } else {
            componentData?.stringRepresentation()?.let {
                val tooltip = if (!it.isEmpty()) " ($it)" else ""
                "$key$tooltip"
            } ?: key
        }
        ImGui.setNextItemOpen(isOpen)
        val nodeOpen = ImGui.treeNode(nodeId(nodePath), label)
        val expandNodeDescendants = expandNode || updateNodeState(nodePath, nodeOpen)
        if (nodeOpen) {
            if (componentData != null) {
                if (!componentData.isEmpty) {
                    componentData.render(objectId, ancestors + objectId, nodePath, expandNodeDescendants)
                }
            } else {
                ImGui.textColored(Color.RED.integer, "Данные отсутствуют")
            }
            ImGui.treePop()
        }
    }

    private fun updateNodeState(nodePath: List<String>, isOpen: Boolean): Boolean {
        val recursiveClick = ImGui.getIO().keyShift && ImGui.isItemClicked()
        val toggled = ImGui.isItemToggledOpen()
        if (!toggled && !recursiveClick) return false

        val targetOpen = if (toggled) isOpen else !isOpen
        if (targetOpen) {
            openNodes += nodePath
            if (recursiveClick && !toggled) {
                recursiveOpenRequests += nodePath
            }
        } else {
            openNodes -= nodePath
            recursiveOpenRequests -= nodePath
            if (recursiveClick) {
                openNodes.removeAll { path ->
                    path.size > nodePath.size && path.take(nodePath.size) == nodePath
                }
                recursiveOpenRequests.removeAll { path ->
                    path.size > nodePath.size && path.take(nodePath.size) == nodePath
                }
            }
        }
        return recursiveClick && toggled && targetOpen
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
            is InspectionPrimitive.Other -> string
            is InspectionPrimitive.Str -> string
            is InspectionPrimitive.Uuid -> string
            is InspectionPrimitive.Id -> id.full
            is InspectionPrimitive.Char -> char.toString()
        }
    }

    private fun InspectionObject.render(
        objectId: Int?,
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
                        objectId,
                        index.toString(),
                        ancestors,
                        nodePath + "index:$index",
                        expandDescendants
                    )
                }
            }
            is InspectionObject.Table -> {
                values.forEach { (key, entry) ->
                    entry.render(objectId, key, ancestors, nodePath + "key:$key", expandDescendants)
                }
            }

            is InspectionObject.PlayerPhysics -> {
                ImGui.checkbox("noClip", noClip)
            }
        }
    }

    private fun InspectionValue.render(
        objectId: Int?,
        key: String,
        ancestors: Set<Int> = emptySet(),
        nodePath: List<String> = emptyList(),
        expandDescendants: Boolean = false
    ) {
        val address = objectId?.let { InspectionPrimitiveAddress(it, key) }
        val customValue = editedPrimitives[address]?.edited

        fun <T> InspectionPrimitive.cast(): T? {
            return this as? T
        }

        when(this) {
            InspectionValue.Null -> {
                ImGui.text("$key:")
                ImGui.sameLine()
                ImGui.textColored(CommonColors.GRAY, "null")
            }

            is InspectionValue.Primitive -> when(val primitive = primitive) {
                is InspectionPrimitive.Bool -> {
                    val boolValue = ImBoolean(customValue?.cast<InspectionPrimitive.Bool>()?.bool ?: primitive.bool)
                    if (editableProperty(key, objectId, null) { ImGui.checkbox(it, boolValue) }) {
                        tryEditPrimitiveValue(InspectionPrimitive.Bool(boolValue.get()), primitive, address, false)
                    }
                }
                is InspectionPrimitive.Double -> ImGui. text("$key: " + primitive.string)
                is InspectionPrimitive.Enum -> ImGui.text("$key: " + primitive.string)
                is InspectionPrimitive.Int -> ImGui.text("$key: " + primitive.string)
                is InspectionPrimitive.Other -> ImGui.text("$key: " + primitive.string)
                is InspectionPrimitive.Str -> {
                    val currentText = customValue?.cast<InspectionPrimitive.Str>()?.string ?: primitive.string
                    val textValue = ImString(
                        currentText,
                        maxOf(256, currentText.toByteArray().size + 64)
                    )
                    if (editableProperty(key, objectId, 600f) { ImGui.inputText(it, textValue) }) {
                        tryEditPrimitiveValue(InspectionPrimitive.Str(textValue.get()), primitive, address, false)
                    }
                }
                is InspectionPrimitive.Uuid -> ImGui.text("$key: " + primitive.string)
                is InspectionPrimitive.Id -> {
                    val currentId = customValue?.cast<InspectionPrimitive.Id>()?.id ?: primitive.id
                    val textValue = ImString(
                        currentId.full,
                        maxOf(256, currentId.full.toByteArray().size + 64)
                    )
                    if (editableProperty(key, objectId, 600f) { ImGui.inputText(it, textValue) }) {
                        tryEditPrimitiveValue(InspectionPrimitive.Id(EngineId(textValue.get())), primitive, address, false)
                    }
                }
                is InspectionPrimitive.Char -> ImGui.text("$key: " + "'" + primitive.char + "'")
            }
            is InspectionValue.Reference -> {
                treeNode(key, id, ancestors, nodePath, expandDescendants)
            }
        }
    }

    private fun tryEditPrimitiveValue(
        value: InspectionPrimitive,
        original: InspectionPrimitive,
        address: InspectionPrimitiveAddress?,
        readonly: Boolean,
    ) {
        if (address == null || readonly) return
        val firstOriginal = editedPrimitives[address]?.original ?: original
        editedPrimitives[address] = PendingEdit(firstOriginal, value)
        inspection.editValue(address.objectId, address.key, value)
    }

    private fun editableProperty(key: String, objectId: Int?, width: Float? = 200f, input: (labelId: String) -> Boolean): Boolean {
        ImGui.alignTextToFramePadding()
        ImGui.textUnformatted("$key:")
        ImGui.sameLine()

        width?.let { ImGui.setNextItemWidth(it) }
        return input("##input-$objectId-$key")
    }
}
