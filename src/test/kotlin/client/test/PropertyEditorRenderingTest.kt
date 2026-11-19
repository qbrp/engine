package org.lain.engine.client.test

import imgui.ImGui
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lain.engine.client.render.ui.property.PropertyEditor
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
import org.lain.engine.util.property.ScalarKind

class PropertyEditorRenderingTest {
    @Test
    fun expandedTreeUsesOneTableRowPerProperty() {
        val rootRef = PropertyReference(1)
        val childRef = PropertyReference(2)
        val recordType = PropertyTypeDescriptorId(1)
        val boolType = PropertyTypeDescriptorId(2)
        val rootPath = PropertyPathDto(emptyList())
        val childPath = rootPath.withSegment(PropertyPathDto.Segment.Field("child"))
        val flagPath = childPath.withSegment(PropertyPathDto.Segment.Field("enabled"))
        val parentPath = childPath.withSegment(PropertyPathDto.Segment.Field("parent"))
        val missingPath = rootPath.withSegment(PropertyPathDto.Segment.Field("optional"))
        val root = Property(PropertyValue.Reference(rootRef), rootPath, recordType)
        val child = Property(PropertyValue.Reference(childRef), childPath, recordType)
        val flag = Property(PropertyValue.Primitive(PropertyScalar.Bool(true)), flagPath, boolType)
        val parent = Property(PropertyValue.Reference(rootRef), parentPath, recordType)
        val missing = Property(null, missingPath, boolType)
        val tree = PropertyTree(
            listOf(root, child, flag, parent, missing).associateBy { it.path },
            mapOf(
                rootRef to PropertyCompound.Record(mapOf("child %s %s" to child, "optional" to missing)),
                childRef to PropertyCompound.Record(mapOf("enabled" to flag, "parent" to parent)),
            ),
            mapOf(
                recordType to PropertyTypeDescriptor.Record("test.Node", emptyList()),
                boolType to PropertyTypeDescriptor.Scalar(ScalarKind.BOOL),
            ),
        )
        val editor = PropertyEditor("test", root, tree) { _, _ -> }
        withImGui {
            editor.expandAll = true
            renderFrame(editor, expectedLastRow = 4)
            editor.expandAll = false
            renderFrame(editor, expectedLastRow = 0)

            val replacementRoot = Property(PropertyValue.Primitive(PropertyScalar.Bool(false)), rootPath, boolType)
            editor.update(replacementRoot, PropertyTree(mapOf(rootPath to replacementRoot), emptyMap(), tree.types))
            renderFrame(editor, expectedLastRow = 0)
        }
    }

    @Test
    fun editingBooleanInTableSendsUpdatedValue() {
        val reference = PropertyReference(1)
        val recordType = PropertyTypeDescriptorId(1)
        val boolType = PropertyTypeDescriptorId(2)
        val rootPath = PropertyPathDto(emptyList())
        val flagPath = rootPath.withSegment(PropertyPathDto.Segment.Field("enabled"))
        val root = Property(PropertyValue.Reference(reference), rootPath, recordType)
        val flag = Property(PropertyValue.Primitive(PropertyScalar.Bool(false)), flagPath, boolType)
        val tree = PropertyTree(
            mapOf(rootPath to root, flagPath to flag),
            mapOf(reference to PropertyCompound.Record(mapOf("enabled" to flag))),
            mapOf(
                recordType to PropertyTypeDescriptor.Record("test.Node", emptyList()),
                boolType to PropertyTypeDescriptor.Scalar(ScalarKind.BOOL),
            ),
        )
        var editedProperty: Property? = null
        var edit: PropertyEdit? = null
        val editor = PropertyEditor("test", root, tree) { property, operation ->
            editedProperty = property
            edit = operation
        }
        editor.expandAll = true

        withImGui {
            val (x, y) = renderCheckboxFrame(editor)
            val io = ImGui.getIO()
            io.addMousePosEvent(x, y)
            renderCheckboxFrame(editor)
            io.addMouseButtonEvent(0, true)
            renderCheckboxFrame(editor)
            io.addMouseButtonEvent(0, false)
            renderCheckboxFrame(editor)

            assertEquals(flag, editedProperty)
            assertEquals(PropertyEdit.Set(PropertyInput.Scalar(PropertyScalar.Bool(true))), edit)
        }
    }

    @Test
    fun enumBeforeFollowingRowsDoesNotSendEdits() {
        val reference = PropertyReference(1)
        val recordType = PropertyTypeDescriptorId(1)
        val enumType = PropertyTypeDescriptorId(2)
        val boolType = PropertyTypeDescriptorId(3)
        val rootPath = PropertyPathDto(emptyList())
        val enumPath = rootPath.withSegment(PropertyPathDto.Segment.Field("mode"))
        val flagPath = rootPath.withSegment(PropertyPathDto.Segment.Field("enabled"))
        val root = Property(PropertyValue.Reference(reference), rootPath, recordType)
        val mode = Property(PropertyValue.Primitive(PropertyScalar.Enum("FIRST")), enumPath, enumType)
        val flag = Property(PropertyValue.Primitive(PropertyScalar.Bool(true)), flagPath, boolType)
        val tree = PropertyTree(
            listOf(root, mode, flag).associateBy { it.path },
            mapOf(reference to PropertyCompound.Record(linkedMapOf("mode" to mode, "enabled" to flag))),
            mapOf(
                recordType to PropertyTypeDescriptor.Record("test.Node", emptyList()),
                enumType to PropertyTypeDescriptor.Enum("test.Mode", listOf("FIRST", "SECOND")),
                boolType to PropertyTypeDescriptor.Scalar(ScalarKind.BOOL),
            ),
        )
        val edits = mutableListOf<PropertyEdit>()
        val editor = PropertyEditor("test", root, tree) { _, edit -> edits += edit }
        editor.expandAll = true

        withImGui {
            repeat(3) { renderFrame(editor, expectedLastRow = 2) }
            assertTrue(edits.isEmpty())
        }
    }

    @Test
    fun selectingEnumValueSendsOneEdit() {
        val enumType = PropertyTypeDescriptorId(1)
        val path = PropertyPathDto(emptyList())
        val property = Property(PropertyValue.Primitive(PropertyScalar.Enum("FIRST")), path, enumType)
        val tree = PropertyTree(
            mapOf(path to property),
            emptyMap(),
            mapOf(enumType to PropertyTypeDescriptor.Enum("test.Mode", listOf("FIRST", "SECOND"))),
        )
        val edits = mutableListOf<Pair<Property, PropertyEdit>>()
        val editor = PropertyEditor("test", property, tree) { editedProperty, edit ->
            edits += editedProperty to edit
        }

        withImGui {
            val (combo, secondItem) = renderEnumFrame(editor)
            val io = ImGui.getIO()
            io.addMousePosEvent(combo.first, combo.second)
            renderEnumFrame(editor)
            io.addMouseButtonEvent(0, true)
            renderEnumFrame(editor)
            io.addMouseButtonEvent(0, false)
            renderEnumFrame(editor)
            io.addMousePosEvent(secondItem.first, secondItem.second)
            renderEnumFrame(editor)
            io.addMouseButtonEvent(0, true)
            renderEnumFrame(editor)
            io.addMouseButtonEvent(0, false)
            renderEnumFrame(editor)
            renderEnumFrame(editor)

            assertEquals(
                listOf(property to PropertyEdit.Set(PropertyInput.Scalar(PropertyScalar.Enum("SECOND")))),
                edits,
            )
        }
    }

    private inline fun withImGui(block: () -> Unit) {
        ImGui.createContext()
        try {
            val io = ImGui.getIO()
            io.setIniFilename(null)
            io.setDisplaySize(800f, 600f)
            io.setDeltaTime(1f / 60f)
            io.fonts.addFontDefault()
            assertTrue(io.fonts.build())
            block()
        } finally {
            ImGui.destroyContext()
        }
    }

    private fun renderCheckboxFrame(editor: PropertyEditor): Pair<Float, Float> {
        ImGui.newFrame()
        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(700f, 500f)
        ImGui.begin("Property test")
        assertTrue(ImGui.beginTable("properties", 3))
        editor.renderRoot("root")
        val min = ImGui.getItemRectMin()
        val max = ImGui.getItemRectMax()
        // EndTable оставляет прямоугольник вложенной таблицы; checkbox находится у её левого края.
        val center = min.x + ImGui.getStyle().cellPaddingX + ImGui.getFrameHeight() / 2f to (min.y + max.y) / 2f
        ImGui.endTable()
        ImGui.end()
        ImGui.render()
        return center
    }

    private fun renderEnumFrame(editor: PropertyEditor): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        ImGui.newFrame()
        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(700f, 500f)
        ImGui.begin("Property test")
        assertTrue(ImGui.beginTable("properties", 3))
        editor.renderRoot("root")
        val min = ImGui.getItemRectMin()
        val max = ImGui.getItemRectMax()
        val combo = min.x + 8f to (min.y + max.y) / 2f
        val secondItem = min.x + 8f to max.y + 3f + ImGui.getTextLineHeightWithSpacing() * 1.5f
        ImGui.endTable()
        ImGui.end()
        ImGui.render()
        return combo to secondItem
    }

    private fun renderFrame(editor: PropertyEditor, expectedLastRow: Int) {
        ImGui.newFrame()
        ImGui.setNextWindowSize(700f, 500f)
        ImGui.begin("Property test")
        assertTrue(ImGui.beginTable("properties", 3))
        editor.renderRoot("root") { assertEquals(0, ImGui.tableGetColumnIndex()) }
        assertEquals(expectedLastRow, ImGui.tableGetRowIndex())
        ImGui.endTable()
        ImGui.end()
        ImGui.render()
        assertTrue(ImGui.getDrawData().totalVtxCount > 0)
    }
}
