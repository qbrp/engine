@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org.lain.engine.test

import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lain.engine.script.SInt
import org.lain.engine.script.SString
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.ScriptValuePrimitive
import org.lain.engine.util.property.PropertyCompound
import org.lain.engine.util.property.PropertyLocation
import org.lain.engine.util.property.PropertyPath
import org.lain.engine.util.property.PropertyPathDto
import org.lain.engine.util.property.PropertyScalar
import org.lain.engine.util.property.PropertyTreeBuilder
import org.lain.engine.util.property.PropertyTree
import org.lain.engine.util.property.PropertyTypeDescriptor
import org.lain.engine.util.property.PropertyValue
import org.lain.engine.util.property.ScriptValueBinding
import org.lain.engine.util.property.inspectScriptValue
import org.lain.engine.util.property.jvmProperty
import org.lain.engine.util.property.reference
import java.util.IdentityHashMap
import kotlin.reflect.typeOf

class PropertySnapshotTest : EngineTest() {
    @Test
    fun reservedReferenceIsBuiltOnceAndReused() = with(PropertyTreeBuilder(TestComponentWorld())) {
        val value = Any()
        val reserved = reserveReference(value)
        var builds = 0
        assertNull(compounds[reserved.objectId])

        val built = value.reference {
            builds++
            PropertyCompound.Record(emptyMap())
        }
        val reused = value.reference {
            builds++
            PropertyCompound.Record(emptyMap())
        }

        assertEquals(reserved, built)
        assertEquals(built, reused)
        assertEquals(1, builds)
        assertEquals(PropertyCompound.Record(emptyMap()), compounds[built.objectId])
    }

    @Test
    fun equalButDistinctObjectsHaveIndependentReferences() = with(PropertyTreeBuilder(TestComponentWorld())) {
        val first = MapKey("same")
        val second = MapKey("same")
        assertEquals(first, second)

        assertNotEquals(reserveReference(first), reserveReference(second))
    }

    @Test
    fun primitiveMapKeyAndValueHaveDistinctPaths() = with(PropertyTreeBuilder(TestComponentWorld())) {
        val root = mapOf("name" to 7).jvmProperty(typeOf<Map<String, Int>>(), PropertyLocation())
        val table = compound(root.value) as PropertyCompound.Table
        val entry = table.entries.single()
        val keyValue = PropertyValue.Primitive(PropertyScalar.Str("name"))

        assertEquals(keyValue, entry.key.value)
        assertEquals(PropertyValue.Primitive(PropertyScalar.Int(7)), entry.value.value)
        assertEquals(listOf(PropertyPathDto.Segment.EntryKey(keyValue)), entry.key.path.segments)
        assertEquals(listOf(PropertyPathDto.Segment.EntryValue(keyValue)), entry.value.path.segments)
        assertEquals(listOf(PropertyPath.Segment.EntryKey("name")), livePaths.getValue(entry.key.path).segments)
        assertEquals(listOf(PropertyPath.Segment.EntryValue("name")), livePaths.getValue(entry.value.path).segments)
        assertSame(entry.key, properties.getValue(entry.key.path))
        assertSame(entry.value, properties.getValue(entry.value.path))
    }

    @Test
    fun charKeyUsesScalarRepresentation() = with(PropertyTreeBuilder(TestComponentWorld())) {
        val root = mapOf('x' to 7).jvmProperty(typeOf<Map<Char, Int>>(), PropertyLocation())
        val entry = (compound(root.value) as PropertyCompound.Table).entries.single()
        val keyValue = PropertyValue.Primitive(PropertyScalar.Char('x'))

        assertEquals(keyValue, entry.key.value)
        assertEquals(listOf(PropertyPathDto.Segment.EntryKey(keyValue)), entry.key.path.segments)
        assertEquals(listOf(PropertyPathDto.Segment.EntryValue(keyValue)), entry.value.path.segments)
    }

    @Test
    fun objectKeyIsReservedBeforeItsFieldsAreBuilt() = with(PropertyTreeBuilder(TestComponentWorld())) {
        val key = MapKey("name")
        val root = mapOf(key to 7).jvmProperty(typeOf<Map<MapKey, Int>>(), PropertyLocation())
        val entry = (compound(root.value) as PropertyCompound.Table).entries.single()
        val keyReference = entry.key.value as PropertyValue.Reference
        val record = compound(keyReference) as PropertyCompound.Record

        assertEquals(listOf(PropertyPathDto.Segment.EntryKey(keyReference)), entry.key.path.segments)
        assertEquals(listOf(PropertyPathDto.Segment.EntryValue(keyReference)), entry.value.path.segments)
        assertEquals(
            listOf(PropertyPathDto.Segment.EntryKey(keyReference), PropertyPathDto.Segment.Field("name")),
            record.fields.getValue("name").path.segments,
        )
        assertEquals(PropertyValue.Primitive(PropertyScalar.Str("name")), record.fields.getValue("name").value)
    }

    @Test
    fun cyclicObjectKeyReferencesItsReservedObject() = with(PropertyTreeBuilder(TestComponentWorld())) {
        val key = CyclicKey(null)
        key.next = key
        val root = mapOf(key to "value").jvmProperty(typeOf<Map<CyclicKey, String>>(), PropertyLocation())
        val entry = (compound(root.value) as PropertyCompound.Table).entries.single()
        val keyReference = entry.key.value as PropertyValue.Reference
        val record = compound(keyReference) as PropertyCompound.Record

        assertEquals(keyReference, record.fields.getValue("next").value)
        assertEquals(
            listOf(PropertyPathDto.Segment.EntryKey(keyReference), PropertyPathDto.Segment.Field("next")),
            record.fields.getValue("next").path.segments,
        )
        val tree = build().tree
        val treeRecord = tree.compounds.getValue(keyReference.objectId) as PropertyCompound.Record
        assertEquals(keyReference, treeRecord.fields.getValue("next").value)
    }

    @Test
    fun nullableMapKeyAndValueKeepTheirPathsAndTypes() = with(PropertyTreeBuilder(TestComponentWorld())) {
        val root = mapOf<String?, Int?>(null to null)
            .jvmProperty(typeOf<Map<String?, Int?>>(), PropertyLocation())
        val entry = (compound(root.value) as PropertyCompound.Table).entries.single()

        assertNull(entry.key.value)
        assertNull(entry.value.value)
        assertEquals(listOf(PropertyPathDto.Segment.EntryKey(null)), entry.key.path.segments)
        assertEquals(listOf(PropertyPathDto.Segment.EntryValue(null)), entry.value.path.segments)
        assertTrue(typeDescriptors[entry.key.type] is PropertyTypeDescriptor.Nullable)
        assertTrue(typeDescriptors[entry.value.type] is PropertyTypeDescriptor.Nullable)

        val tree = build().tree
        val bytes = ProtoBuf.encodeToByteArray(PropertyTree.serializer(), tree)
        assertEquals(tree, ProtoBuf.decodeFromByteArray(PropertyTree.serializer(), bytes))
    }

    @Test
    fun aliasedKeyAndValueShareReferenceButHaveDistinctEntryPaths() = with(PropertyTreeBuilder(TestComponentWorld())) {
        val key = MapKey("shared")
        val root = mapOf(key to key).jvmProperty(typeOf<Map<MapKey, MapKey>>(), PropertyLocation())
        val entry = (compound(root.value) as PropertyCompound.Table).entries.single()

        assertEquals(entry.key.value, entry.value.value)
        assertNotEquals(entry.key.path, entry.value.path)
        assertEquals(2, compounds.size)
    }

    @Test
    fun indistinguishableSerializedKeysAreRejected() = with(PropertyTreeBuilder(TestComponentWorld())) {
        val map: Map<*, *> = linkedMapOf<Any, Int>(1L to 1, 1.0 to 2)

        assertThrows(IllegalStateException::class.java) {
            map.jvmProperty(typeOf<Map<*, *>>(), PropertyLocation())
        }
    }

    @Test
    fun identityMapKeepsEqualObjectKeysSeparate() = with(PropertyTreeBuilder(TestComponentWorld())) {
        val first = MapKey("same")
        val second = MapKey("same")
        val map = IdentityHashMap<MapKey, Int>().apply {
            put(first, 1)
            put(second, 2)
        }
        val root = map.jvmProperty(typeOf<IdentityHashMap<MapKey, Int>>(), PropertyLocation())
        val entries = (compound(root.value) as PropertyCompound.Table).entries

        assertEquals(2, entries.size)
        assertNotEquals(entries[0].key.value, entries[1].key.value)
        assertNotEquals(entries[0].key.path, entries[1].key.path)
    }

    @Test
    fun scriptTableUsesDistinctPathsForKeysAndValues() = with(PropertyTreeBuilder(TestComponentWorld())) {
        val key = TestPrimitive(SString("name"))
        val value = TestPrimitive(SInt(7))
        val table = TestTable(listOf(ScriptValueBinding.Table.Entry(key, value)))
        val root = table.inspectScriptValue(PropertyLocation())
        val entry = (compound(root.value) as PropertyCompound.Table).entries.single()
        val keyValue = PropertyValue.Primitive(PropertyScalar.Str("name"))

        assertEquals(listOf(PropertyPathDto.Segment.EntryKey(keyValue)), entry.key.path.segments)
        assertEquals(listOf(PropertyPathDto.Segment.EntryValue(keyValue)), entry.value.path.segments)
        assertEquals(PropertyValue.Primitive(PropertyScalar.Int(7)), entry.value.value)
        assertSame(entry.key, properties.getValue(entry.key.path))
        assertSame(entry.value, properties.getValue(entry.value.path))
    }

    @Test
    fun scriptTableKeyIsReservedBeforeItsChildrenAreBuilt() = with(PropertyTreeBuilder(TestComponentWorld())) {
        val innerKey = TestPrimitive(SString("inner"))
        val innerValue = TestPrimitive(SInt(1))
        val tableKey = TestTable(listOf(ScriptValueBinding.Table.Entry(innerKey, innerValue)))
        val table = TestTable(listOf(ScriptValueBinding.Table.Entry(tableKey, TestPrimitive(SInt(2)))))
        val root = table.inspectScriptValue(PropertyLocation())
        val outerEntry = (compound(root.value) as PropertyCompound.Table).entries.single()
        val keyReference = outerEntry.key.value as PropertyValue.Reference
        val innerEntry = (compound(keyReference) as PropertyCompound.Table).entries.single()

        assertEquals(listOf(PropertyPathDto.Segment.EntryKey(keyReference)), outerEntry.key.path.segments)
        assertEquals(listOf(PropertyPathDto.Segment.EntryValue(keyReference)), outerEntry.value.path.segments)
        assertEquals(
            listOf(
                PropertyPathDto.Segment.EntryKey(keyReference),
                PropertyPathDto.Segment.EntryValue(PropertyValue.Primitive(PropertyScalar.Str("inner"))),
            ),
            innerEntry.value.path.segments,
        )
    }

    private fun PropertyTreeBuilder.compound(value: PropertyValue?): PropertyCompound {
        val reference = value as PropertyValue.Reference
        return compounds.getValue(reference.objectId)
    }

    data class MapKey(val name: String)

    class CyclicKey(var next: CyclicKey?)

    private class TestPrimitive(override val value: ScriptValuePrimitive) : ScriptValueBinding.Primitive

    private class TestTable(
        override val entries: List<ScriptValueBinding.Table.Entry>,
    ) : ScriptValueBinding.Table {
        override fun replace(entry: ScriptValueBinding.Table.Entry, value: ScriptValue) = error("Not used")
        override fun set(key: ScriptValue, value: ScriptValue) = error("Not used")
    }
}
