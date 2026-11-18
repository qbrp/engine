package org.lain.engine.client.test

import imgui.type.ImBoolean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.lain.engine.client.render.ui.property.PropertyEditor
import org.lain.engine.client.render.ui.property.formatString
import org.lain.engine.util.property.Property
import org.lain.engine.util.property.PropertyPathDto
import org.lain.engine.util.property.PropertyScalar
import org.lain.engine.util.property.PropertyTree
import org.lain.engine.util.property.PropertyTypeDescriptor
import org.lain.engine.util.property.PropertyTypeDescriptorId
import org.lain.engine.util.property.ScalarKind

class PropertyTypeFormattingTest {
    @Test
    fun decimalValuesKeepTheirNumericFormWhenRoundingIsDisabled() {
        val type = PropertyTypeDescriptorId(1)
        val root = Property(null, PropertyPathDto(emptyList()), type)
        val editor = PropertyEditor(
            "test", root, PropertyTree(emptyMap(), emptyMap(), emptyMap()), ImBoolean(false),
        ) { _, _ -> }

        with(editor) {
            assertEquals("1.25", PropertyScalar.Double(1.25).formatString())
            assertEquals("1.2", PropertyScalar.Float(1.2f).formatString())
        }
    }

    @Test
    fun nestedTypesUseShortNamesAndCanShowFullNames() {
        val stringId = PropertyTypeDescriptorId(1)
        val recordId = PropertyTypeDescriptorId(2)
        val collectionId = PropertyTypeDescriptorId(3)
        val nullableId = PropertyTypeDescriptorId(4)
        val mapId = PropertyTypeDescriptorId(5)
        val enumId = PropertyTypeDescriptorId(6)
        val types = mapOf(
            stringId to PropertyTypeDescriptor.Scalar(ScalarKind.STRING),
            recordId to PropertyTypeDescriptor.Record("org.lain.example.Position", emptyList()),
            collectionId to PropertyTypeDescriptor.Collection(recordId),
            nullableId to PropertyTypeDescriptor.Nullable(collectionId),
            mapId to PropertyTypeDescriptor.Map(stringId, nullableId),
            enumId to PropertyTypeDescriptor.Enum("org.lain.example.State", listOf("ACTIVE")),
        )
        val root = Property(null, PropertyPathDto(emptyList()), mapId)
        val editor = PropertyEditor("test", root, PropertyTree(emptyMap(), emptyMap(), types)) { _, _ -> }

        with(editor) {
            assertEquals("map<string, collection<Position>?>", types.getValue(mapId).formatString())
            assertEquals(
                "map<string, collection<org.lain.example.Position>?>",
                types.getValue(mapId).formatString(fullName = true),
            )
            assertEquals("State", types.getValue(enumId).formatString())
            assertEquals("org.lain.example.State", types.getValue(enumId).formatString(fullName = true))
        }
    }
}
