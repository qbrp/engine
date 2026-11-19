package org.lain.engine.client.render.ui.property

import imgui.ImGui
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImBoolean
import imgui.type.ImDouble
import imgui.type.ImFloat
import imgui.type.ImInt
import imgui.type.ImString
import org.lain.engine.script.EngineId
import org.lain.engine.util.Color
import org.lain.engine.util.property.Property
import org.lain.engine.util.property.PropertyCompound
import org.lain.engine.util.property.PropertyEdit
import org.lain.engine.util.property.PropertyInput
import org.lain.engine.util.property.PropertyPathDto
import org.lain.engine.util.property.PropertyReference
import org.lain.engine.util.property.PropertyScalar
import org.lain.engine.util.property.PropertyTree
import org.lain.engine.util.property.PropertyTypeDescriptor
import org.lain.engine.util.property.PropertyTypeDescriptorId
import org.lain.engine.util.property.PropertyValue
import kotlin.collections.component1
import kotlin.collections.component2

data class PendingEdit(
    val original: PropertyValue?,
    val edited: PropertyValue,
    val sendImmediately: Boolean = true
)

class PropertyEditor(
    val id: String,
    root: Property,
    tree: PropertyTree,
    val shortDoubleFormatting: ImBoolean = ImBoolean(true),
    val showTypes: ImBoolean = ImBoolean(true),
    expandAll: Boolean = false,
    val editValue: (Property, PropertyEdit) -> Unit,
) {
    private val open = mutableSetOf<PropertyPathDto>()
    private val pendingRecursiveExpansions = mutableSetOf<PropertyPathDto>()
    private val propertyEdits = mutableMapOf<PropertyPathDto, PendingEdit>()
    var root: Property = root
        private set
    var tree: PropertyTree = tree
        private set
    var expandAll = expandAll
        set(value) {
            if (!value) open.clear()
            field = value
        }

    fun update(root: Property, tree: PropertyTree) {
        open.removeIf { it !in tree.properties.keys }
        pendingRecursiveExpansions.removeIf { it !in tree.properties.keys }
        propertyEdits.entries.removeIf { (path, edit) ->
            val currentValue = tree.properties[path]?.value
            path !in tree.properties || currentValue == edit.edited || currentValue != edit.original
        }
        this.root = root
        this.tree = tree
    }

    fun renderRoot(label: String, afterLabel: () -> Unit = {}) {
        renderRow(label, root, emptySet(), false, afterLabel)
    }

    fun resolveType(id: PropertyTypeDescriptorId) = tree.types[id] ?: PropertyTypeDescriptor.Unknown

    private fun renderRow(
        label: String,
        property: Property,
        ancestors: Set<PropertyReference>,
        expandDescendants: Boolean,
        afterLabel: () -> Unit = {},
    ) {
        val reference = property.value as? PropertyValue.Reference
        val compound = reference?.let { tree.compounds[it.objectId] }
        val cyclic = reference != null && reference.objectId in ancestors
        val expandable = compound != null && compound !is PropertyCompound.Player && !cyclic

        ImGui.tableNextRow()
        ImGui.tableNextColumn()
        if (expandable) {
            if (expandAll || expandDescendants || property.path in pendingRecursiveExpansions) {
                open += property.path
            }
            ImGui.setNextItemOpen(property.path in open)
        }
        val flags = if (expandable) 0 else ImGuiTreeNodeFlags.Leaf or ImGuiTreeNodeFlags.NoTreePushOnOpen
        val opened = ImGui.treeNodeEx(nodeId(property.path), flags, label.replace("%", "%%"))
        if (expandable) updateNodeState(property.path, opened)
        afterLabel()

        ImGui.tableNextColumn()

        val descriptor = resolveType(property.type)
        val notNullDescriptor = if (descriptor is PropertyTypeDescriptor.Nullable) {
            resolveType(descriptor.valueTypeId)
        } else {
            descriptor
        }

        if (showTypes.get()) {
            val typeName = descriptor.formatString()
            ImGui.textDisabled(typeName)
            val fullTypeName = descriptor.formatString(fullName = true)
            if (typeName != fullTypeName && ImGui.isItemHovered()) {
                ImGui.beginTooltip()
                ImGui.textUnformatted(fullTypeName)
                ImGui.endTooltip()
            }
            ImGui.tableNextColumn()
        }

        val value = property.value
        var focus = false
        if (value == null && propertyEdits[property.path] == null) {
            if (ImGui.selectable("null##$id-${property.path}") &&
                notNullDescriptor is PropertyTypeDescriptor.Scalar
            ) {
                val emptyValue = notNullDescriptor.emptyValue()
                if (emptyValue != null) {
                    propertyEdits[property.path] = PendingEdit(null, emptyValue, false)
                    focus = true
                }
            }
        }

        val pendingEdit = propertyEdits[property.path]

        if (ImGui.beginTable("##${property.path}-layout", 2)) {
            ImGui.tableSetupColumn("Input", ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn("Button", ImGuiTableColumnFlags.WidthFixed)

            ImGui.tableNextColumn()
            ImGui.setNextItemWidth(-1f)
            when (val value = value ?: pendingEdit?.edited) {
                is PropertyValue.Primitive -> {
                    if (focus) ImGui.setKeyboardFocusHere()
                    value.primitive.render(property, descriptor, pendingEdit, focus)
                }
                is PropertyValue.Reference -> when {
                    cyclic -> ImGui.textDisabled("Циклическая ссылка")
                    compound == null -> ImGui.textColored(Color.RED.integer, "Данные отсутствуют")
                    compound is PropertyCompound.Player -> ImGui.textUnformatted(compound.description)
                    compound.isEmpty -> ImGui.textDisabled("empty")
                    !opened -> ImGui.textUnformatted(compound.formatString(ancestors + value.objectId) ?: "")
                }
                else -> {}
            }

            ImGui.tableNextColumn()
            if (descriptor is PropertyTypeDescriptor.Nullable && value != null) {
                ImGui.sameLine()
                if (ImGui.smallButton("null")) {
                    editValue(property, PropertyEdit.Set(PropertyInput.Null))
                }
            }

            ImGui.endTable()
        }

        if (expandable && opened) {
            val expandChildren = pendingRecursiveExpansions.remove(property.path) || expandAll || expandDescendants
            compound.render(ancestors + reference.objectId, expandChildren)
            ImGui.treePop()
        }
    }

    private fun PropertyCompound.render(
        ancestors: Set<PropertyReference>,
        expandDescendants: Boolean
    ) {
        when (this) {
            is PropertyCompound.Collection -> {
                values.forEachIndexed { index, value ->
                    renderRow(index.toString(), value, ancestors, expandDescendants)
                }
            }

            is PropertyCompound.Record -> {
                fields.forEach { (key, entry) ->
                    renderRow(key, entry, ancestors, expandDescendants)
                }
            }

            is PropertyCompound.Table -> {
                entries.forEach { (key, value) ->
                    renderRow(key.value.formatString(ancestors), value, ancestors, expandDescendants)
                }
            }

            is PropertyCompound.PlayerPhysics -> {
                ImGui.tableNextRow()
                ImGui.tableNextColumn()
                if (showTypes.get()) {
                    ImGui.treeNodeEx(
                        "physics-no-clip",
                        ImGuiTreeNodeFlags.Leaf or ImGuiTreeNodeFlags.NoTreePushOnOpen,
                        "noClip"
                    )
                    ImGui.tableNextColumn()
                }
                ImGui.textDisabled("boolean")
                ImGui.tableNextColumn()
                ImGui.textUnformatted(noClip.toString())
            }

            is PropertyCompound.Player -> Unit
        }
    }

    private fun PropertyScalar.render(
        node: Property,
        descriptor: PropertyTypeDescriptor,
        edit: PendingEdit?,
        focused: Boolean,
    ) {
        val customValue = edit?.edited
        val customPrimitive = customValue as? PropertyValue.Primitive

        fun <T : PropertyScalar> customValueAs(): T? {
            return customPrimitive?.primitive as? T
        }

        fun <T : PropertyTypeDescriptor> descriptorAs(): T? {
            return descriptor as? T
        }

        fun canEdit() = edit?.sendImmediately != false || ImGui.isItemDeactivatedAfterEdit()

        when (this) {
            is PropertyScalar.Bool -> {
                val boolValue = ImBoolean(customValueAs<PropertyScalar.Bool>()?.bool ?: bool)
                if (editableProperty(node, focused, null) { ImGui.checkbox(it, boolValue) } && canEdit()) {
                    tryEditPrimitiveValue(PropertyScalar.Bool(boolValue.get()), node, this)
                }
            }

            is PropertyScalar.Double -> {
                val doubleValue = ImDouble(customValueAs<PropertyScalar.Double>()?.double ?: double)
                if (editableProperty(node, focused) { ImGui.inputDouble(it, doubleValue) } && canEdit()) {
                    tryEditPrimitiveValue(PropertyScalar.Double(doubleValue.get()), node, this)
                }
            }

            is PropertyScalar.Float -> {
                val floatValue = ImFloat(customValueAs<PropertyScalar.Float>()?.float ?: float)
                if (editableProperty(node, focused) { ImGui.inputFloat(it, floatValue) } && canEdit()) {
                    tryEditPrimitiveValue(PropertyScalar.Float(floatValue.get()), node, this)
                }
            }

            is PropertyScalar.Int -> {
                val intValue = ImInt(customValueAs<PropertyScalar.Int>()?.int ?: int)
                if (editableProperty(node, focused) { ImGui.inputInt(it, intValue) } && canEdit()) {
                    tryEditPrimitiveValue(PropertyScalar.Int(intValue.get()), node, this)
                }
            }

            is PropertyScalar.Str -> {
                val currentText = customValueAs<PropertyScalar.Str>()?.string ?: string
                val textValue = ImString(
                    currentText,
                    maxOf(256, currentText.toByteArray().size + 64)
                )
                if (editableProperty(node, focused) { ImGui.inputText(it, textValue) } && canEdit()) {
                    tryEditPrimitiveValue(PropertyScalar.Str(textValue.get()), node, this)
                }
            }

            is PropertyScalar.Uuid -> ImGui.text(string)
            is PropertyScalar.Id -> {
                val currentId = customValueAs<PropertyScalar.Id>()?.id ?: id
                val textValue = ImString(
                    currentId.full,
                    maxOf(256, currentId.full.toByteArray().size + 64)
                )
                if (editableProperty(node, focused) { ImGui.inputText(it, textValue) } && canEdit()) {
                    runCatching { EngineId(textValue.get()) }
                        .getOrNull()
                        ?.let {
                            tryEditPrimitiveValue(
                                PropertyScalar.Id(it),
                                node,
                                this
                            )
                        }
                }
            }

            is PropertyScalar.Char -> ImGui.text(char.toString())
            is PropertyScalar.Enum -> {
                val items = descriptorAs<PropertyTypeDescriptor.Enum>()?.values
                val currentName = customValueAs<PropertyScalar.Enum>()?.name ?: name

                if (items != null) {
                    val selectedIndex = ImInt(items.indexOf(currentName))
                    if (ImGui.combo(editableId(node), selectedIndex, items.toTypedArray())) {
                        tryEditPrimitiveValue(PropertyScalar.Enum(items[selectedIndex.get()]), node, this)
                    }
                } else {
                    val textValue = ImString(currentName, maxOf(256, currentName.toByteArray().size + 64))
                    if (editableProperty(node, focused) { ImGui.inputText(it, textValue) } && canEdit()) {
                        tryEditPrimitiveValue(PropertyScalar.Enum(textValue.get()), node, this)
                    }
                }
            }

            is PropertyScalar.Instant -> ImGui.text(formatString())
            is PropertyScalar.EntityRef -> {
                ImGui.textUnformatted(formatString())
                if (ImGui.isItemHovered()) {
                    ImGui.beginTooltip()
                    ImGui.text("Index: ${entityId.index}")
                    ImGui.text("Generation: ${entityId.generation}")
                    persistentId?.let { ImGui.textUnformatted("Persistent id: $it") }
                    debugName?.let { ImGui.textUnformatted("Debug name: $it") }
                    ImGui.endTooltip()
                }
            }
        }
    }

    private fun tryEditPrimitiveValue(
        value: PropertyScalar,
        node: Property,
        originalValue: PropertyScalar
    ) {
        val path = node.path
        val lastEdit = propertyEdits[path] ?: PendingEdit(PropertyValue.Primitive(originalValue), PropertyValue.Primitive(value))
        propertyEdits[path] = lastEdit.copy(edited = PropertyValue.Primitive(value))
        editValue(node, PropertyEdit.Set(PropertyInput.Scalar(value)))
    }

    private fun editableProperty(
        node: Property,
        focused: Boolean,
        width: Float? = -1f,
        input: (labelId: String) -> Boolean
    ): Boolean {
        width?.let { ImGui.setNextItemWidth(it) }
        val changed = input(editableId(node))
        if (!ImGui.isItemActive() && !changed && !focused) {
            propertyEdits.remove(node.path)
        }
        return changed
    }

    private fun editableId(node: Property) = "##input-${node.path}"

    private fun updateNodeState(nodePath: PropertyPathDto, isOpen: Boolean) {
        val recursiveClick = ImGui.getIO().keyShift && ImGui.isItemClicked()
        val toggled = ImGui.isItemToggledOpen()
        if (!toggled && !recursiveClick) return

        val targetOpen = if (toggled) isOpen else !isOpen
        if (targetOpen) {
            open += nodePath
            if (recursiveClick) {
                pendingRecursiveExpansions += nodePath
            }
        } else {
            open -= nodePath
            pendingRecursiveExpansions -= nodePath
            propertyEdits.keys.removeAll { it == nodePath || it.isDescendantOf(nodePath) }
            if (recursiveClick) {
                open.removeAll { path ->
                    path.isDescendantOf(nodePath)
                }
                pendingRecursiveExpansions.removeAll { path ->
                    path.isDescendantOf(nodePath)
                }
            }
        }
    }

    private fun nodeId(nodePath: PropertyPathDto): String {
        return "inspection-tree-$id-$nodePath"
    }
}
