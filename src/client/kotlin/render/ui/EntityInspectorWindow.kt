package org.lain.engine.client.render.ui

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImDouble
import imgui.type.ImInt
import imgui.type.ImString
import net.minecraft.util.CommonColors
import org.lain.engine.client.script.EntityInspection
import org.lain.engine.script.EngineId
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.dev.ComponentInspectionResult
import org.lain.engine.script.dev.EditOperation
import org.lain.engine.script.dev.EntityInspectionSnapshot
import org.lain.engine.script.dev.InputValue
import org.lain.engine.script.dev.InspectionNode
import org.lain.engine.script.dev.InspectionValue
import org.lain.engine.script.dev.InspectionObject
import org.lain.engine.script.dev.InspectionPathDto
import org.lain.engine.script.dev.InspectionPrimitive
import org.lain.engine.util.Color
import org.lain.engine.util.ecs.EngineComponentType
import org.lain.engine.util.ecs.lookupComponentType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EntityInspectorWindow(
    val inspection: EntityInspection,
    val entityName: String,
    val namespacedStorage: NamespacedStorageAccess,
) : ImGuiWindow {
    private val expandAll = ImBoolean(false)
    private val openNodes = mutableSetOf<InspectionPathDto>()
    private val pendingRecursiveExpansions = mutableSetOf<InspectionPathDto>()
    private var abortReason: String? = null

    private val propertyEdits = mutableMapOf<InspectionPathDto, PendingEdit>()

    data class PendingEdit(
        val path: InspectionPathDto,
        val editTargetId: Int,
        val original: InspectionValue,
        val edited: InspectionValue,
    )

    data class RenderedInspectionNode(
        val node: InspectionNode,
        val path: InspectionPathDto,
    ) {
        val editTargetId: Int?
            get() = node.editTargetId

        fun child(
            node: InspectionNode,
            segment: InspectionPathDto.Segment
        ) = RenderedInspectionNode(node, path.withSegment(segment))
    }

    override val title: String = "Инспектор сущности $entityName"

    override fun onClose() {
        inspection.stop()
    }

    fun abort(reason: String) {
        abortReason = reason
        return
    }

    fun onDataUpdated(data: EntityInspectionSnapshot.Dto) {
        propertyEdits.entries.removeIf { (path, edit) ->
            val currentNode = data.nodes[path]
            currentNode == null || currentNode == edit.edited || currentNode != edit.original
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
                val componentPath = InspectionPathDto(component.type, emptyList())
                val expandComponent = expandAll.get() || pendingRecursiveExpansions.remove(componentPath)
                if (expandComponent) {
                    openNodes += componentPath
                }
                ImGui.setNextItemOpen(componentPath in openNodes)

                val componentData = component.data
                val hasError = componentData is ComponentInspectionResult.Error
                val componentType = lookupComponentType(component.type, namespacedStorage.get())
                val meta = when (componentType) {
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

                val componentOpen = ImGui.treeNode(nodeId(componentPath), label.toString())
                updateNodeState(componentPath, componentOpen)

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
                        val componentNode = componentData.node
                        val renderedNode = RenderedInspectionNode(componentNode, componentPath)
                        when (val nodeValue = componentNode.value) {
                            InspectionValue.Null -> {
                                ImGui.textColored(Color.GRAY.integer, "null")
                            }

                            is InspectionValue.Primitive -> componentNode.render(renderedNode)

                            is InspectionValue.Reference -> {
                                val componentObjectData = data.debugObjects[nodeValue.objectId]
                                if (componentObjectData != null) {
                                    if (!componentObjectData.isEmpty) {
                                        componentObjectData.render(
                                            renderedNode,
                                            setOf(nodeValue.objectId),
                                            expandComponent
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
        objectId: Int,
        node: RenderedInspectionNode,
        ancestors: Set<Int>,
        expandDescendants: Boolean
    ) {
        if (objectId in ancestors) {
            ImGui.textColored(Color.GRAY.integer, "cyclic reference $objectId")
            return
        }
        val componentData = inspection.data?.debugObjects[objectId]
        val expandNode = expandAll.get() || expandDescendants || pendingRecursiveExpansions.remove(node.path)
        if (expandNode) {
            openNodes += node.path
        }
        val isOpen = node.path in openNodes

        val tooltip = StringBuilder("")
        if (isOpen && componentData != null) {
            val representation = componentData.stringRepresentation()
            if (!representation.isNullOrEmpty()) {
                tooltip.append("($representation)")
            }
        }

        ImGui.setNextItemOpen(isOpen)
        val nodeOpen = ImGui.treeNode(nodeId(node.path), tooltip.toString())
        updateNodeState(node.path, nodeOpen)
        if (nodeOpen) {
            if (componentData != null) {
                if (!componentData.isEmpty) {
                    componentData.render(node, ancestors + objectId, expandNode)
                }
            } else {
                ImGui.textColored(Color.RED.integer, "Данные отсутствуют")
            }
            ImGui.treePop()
        }
    }

    private fun updateNodeState(nodePath: InspectionPathDto, isOpen: Boolean) {
        val recursiveClick = ImGui.getIO().keyShift && ImGui.isItemClicked()
        val toggled = ImGui.isItemToggledOpen()
        if (!toggled && !recursiveClick) return

        val targetOpen = if (toggled) isOpen else !isOpen
        if (targetOpen) {
            openNodes += nodePath
            if (recursiveClick) {
                pendingRecursiveExpansions += nodePath
            }
        } else {
            openNodes -= nodePath
            pendingRecursiveExpansions -= nodePath
            if (recursiveClick) {
                openNodes.removeAll { path ->
                    path.isDescendantOf(nodePath)
                }
                pendingRecursiveExpansions.removeAll { path ->
                    path.isDescendantOf(nodePath)
                }
            }
        }
    }

    private fun nodeId(nodePath: InspectionPathDto): String {
        return "inspection-node-$nodePath"
    }

    private fun InspectionPathDto.isDescendantOf(parent: InspectionPathDto): Boolean {
        return componentType == parent.componentType &&
            segments.size > parent.segments.size &&
            segments.take(parent.segments.size) == parent.segments
    }

    fun InspectionObject.stringRepresentation(): String? {
        return when (this) {
            is InspectionObject.Collection -> values.joinToString { it.value.stringRepresentation() }
            is InspectionObject.PlayerPhysics -> null
            is InspectionObject.Table -> entries.map { (k, v) -> k.value.stringRepresentation() to v }
                .stringRepresentation()

            is InspectionObject.Record -> fields.map { (property, node) -> property to node }.stringRepresentation()
        }
    }

    private fun List<Pair<String, InspectionNode>>.stringRepresentation(): String {
        return joinToString { (key, value) -> "[${key}: ${value.value.stringRepresentation()}]" }
    }

    fun InspectionValue.stringRepresentation(): String {
        return when (this) {
            InspectionValue.Null -> "null"
            is InspectionValue.Primitive -> primitive.stringRepresentation()
            is InspectionValue.Reference -> {
                val inspectionObject = inspection.data?.debugObjects[objectId]
                inspectionObject?.stringRepresentation() ?: "данные не найдены"
            }
        }
    }

    fun InspectionPrimitive.stringRepresentation(): String {
        return when (this) {
            is InspectionPrimitive.Bool -> bool.toString()
            is InspectionPrimitive.Double -> string
            is InspectionPrimitive.Int -> string
            is InspectionPrimitive.Other -> string
            is InspectionPrimitive.Str -> string
            is InspectionPrimitive.Uuid -> string
            is InspectionPrimitive.Id -> id.full
            is InspectionPrimitive.Char -> char.toString()
            is InspectionPrimitive.EntityRef -> entityId.toString()
            is InspectionPrimitive.Instant -> LocalDateTime.ofInstant(
                Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()
            ).format(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
            )
        }
    }

    private fun InspectionObject.render(
        node: RenderedInspectionNode,
        ancestors: Set<Int>,
        expandDescendants: Boolean
    ) {
        if (isEmpty) {
            ImGui.textColored(CommonColors.GRAY, "empty")
        }
        when (this) {
            is InspectionObject.Collection -> {
                values.forEachIndexed { index, value ->
                    ImGui.text("$index:")
                    ImGui.sameLine()
                    value.render(
                        node.child(value, InspectionPathDto.Segment.Index(index)),
                        ancestors,
                        expandDescendants
                    )
                }
            }

            is InspectionObject.Record -> {
                fields.forEach { (key, entry) ->
                    ImGui.text("$key:")
                    ImGui.sameLine()
                    entry.render(
                        node.child(entry, InspectionPathDto.Segment.Field(key)),
                        ancestors,
                        expandDescendants
                    )
                }
            }

            is InspectionObject.Table -> {
                entries.forEach { (key, value) ->
                    ImGui.text(key.value.stringRepresentation())
                    ImGui.sameLine()
                    ImGui.text(" : ")
                    ImGui.sameLine()
                    value.render(
                        node.child(value, InspectionPathDto.Segment.TableKey(key.value)),
                        ancestors,
                        expandDescendants
                    )
                }
            }

            is InspectionObject.PlayerPhysics -> {
                ImGui.checkbox("noClip", noClip)
            }
        }
    }

    private fun InspectionNode.render(
        node: RenderedInspectionNode,
        ancestors: Set<Int> = emptySet(),
        expandDescendants: Boolean = false
    ) {
        val edit = propertyEdits[node.path]

        when (val value = value) {
            InspectionValue.Null -> ImGui.textColored(CommonColors.GRAY, "null")
            is InspectionValue.Primitive -> value.primitive.render(node, edit)
            is InspectionValue.Reference -> {
                treeNode(value.objectId, node, ancestors, expandDescendants)
            }
        }
    }

    private fun InspectionPrimitive.render(
        node: RenderedInspectionNode,
        edit: PendingEdit?,
    ) {
        val customValue = edit?.edited
        val customPrimitive = customValue as? InspectionValue.Primitive

        fun <T : InspectionPrimitive> customValueAs(): T? {
            return customPrimitive?.primitive as? T
        }

        when (this) {
            is InspectionPrimitive.Bool -> {
                val boolValue = ImBoolean(customValueAs<InspectionPrimitive.Bool>()?.bool ?: bool)
                if (editableProperty(node, null) { ImGui.checkbox(it, boolValue) }) {
                    tryEditPrimitiveValue(InspectionPrimitive.Bool(boolValue.get()), node, this)
                }
            }

            is InspectionPrimitive.Double -> {
                val doubleValue = ImDouble(customValueAs<InspectionPrimitive.Double>()?.double ?: double)
                if (editableProperty(node) { ImGui.inputDouble(it, doubleValue) }) {
                    tryEditPrimitiveValue(InspectionPrimitive.Double(doubleValue.get()), node, this)
                }
            }

            is InspectionPrimitive.Int -> {
                val intValue = ImInt(customValueAs<InspectionPrimitive.Int>()?.int ?: int)
                if (editableProperty(node) { ImGui.inputInt(it, intValue) }) {
                    tryEditPrimitiveValue(InspectionPrimitive.Int(intValue.get()), node, this)
                }
            }

            is InspectionPrimitive.Other -> ImGui.text(string)
            is InspectionPrimitive.Str -> {
                val currentText = customValueAs<InspectionPrimitive.Str>()?.string ?: string
                val textValue = ImString(
                    currentText,
                    maxOf(256, currentText.toByteArray().size + 64)
                )
                if (editableProperty(node, 600f) { ImGui.inputText(it, textValue) }) {
                    tryEditPrimitiveValue(InspectionPrimitive.Str(textValue.get()), node, this)
                }
            }

            is InspectionPrimitive.Uuid -> ImGui.text(string)
            is InspectionPrimitive.Id -> {
                val currentId = customValueAs<InspectionPrimitive.Id>()?.id ?: id
                val textValue = ImString(
                    currentId.full,
                    maxOf(256, currentId.full.toByteArray().size + 64)
                )
                if (editableProperty(node, 600f) { ImGui.inputText(it, textValue) }) {
                    tryEditPrimitiveValue(
                        InspectionPrimitive.Id(
                            EngineId(textValue.get())
                        ),
                        node,
                        this
                    )
                }
            }

            is InspectionPrimitive.Char -> ImGui.text(char.toString())
            is InspectionPrimitive.Instant -> ImGui.text(stringRepresentation())
            is InspectionPrimitive.EntityRef -> ImGui.text(stringRepresentation())
        }
    }

    private fun tryEditPrimitiveValue(
        value: InspectionPrimitive,
        node: RenderedInspectionNode,
        originalValue: InspectionPrimitive
    ) {
        val editTargetId = node.editTargetId ?: return
        val path = node.path
        val firstOriginal = propertyEdits[path]?.original ?: InspectionValue.Primitive(originalValue)
        propertyEdits[path] = PendingEdit(path, editTargetId, firstOriginal, InspectionValue.Primitive(value))
        inspection.editValue(editTargetId, EditOperation.SetProperty(InputValue.Primitive(value)))
    }

    private fun editableProperty(
        node: RenderedInspectionNode,
        width: Float? = 200f,
        input: (labelId: String) -> Boolean
    ): Boolean {
        width?.let { ImGui.setNextItemWidth(it) }
        return input("##input-${node.path}-${node.editTargetId}")
    }
}
