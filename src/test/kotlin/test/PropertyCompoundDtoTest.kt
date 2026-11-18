@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org.lain.engine.test

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.EntityId
import org.lain.engine.script.ComponentInspectionResult
import org.lain.engine.script.ComponentInspectionSnapshot
import org.lain.engine.script.EntityInspectionSnapshot
import org.lain.engine.script.EntityInspectionSnapshotDto
import org.lain.engine.util.property.Property
import org.lain.engine.util.property.PropertyCompound
import org.lain.engine.util.property.PropertyLocation
import org.lain.engine.util.property.PropertyPath
import org.lain.engine.util.property.PropertyPathDto
import org.lain.engine.util.property.PropertyReference
import org.lain.engine.util.property.PropertyScalar
import org.lain.engine.util.property.PropertyTree
import org.lain.engine.util.property.PropertyTreeBuilder
import org.lain.engine.util.property.PropertyTypeDescriptor
import org.lain.engine.util.property.PropertyTypeDescriptorId
import org.lain.engine.util.property.PropertyValue
import org.lain.engine.util.property.ScalarKind
import org.lain.engine.util.property.jvmProperty
import kotlin.reflect.typeOf

class PropertyCompoundDtoTest : EngineTest() {
    @Test
    fun nullPropertySurvivesProtobufRoundTrip() {
        val property = Property(null, PropertyPathDto(emptyList()), PropertyTypeDescriptorId(1))

        val bytes = ProtoBuf.encodeToByteArray(Property.serializer(), property)
        assertEquals(property, ProtoBuf.decodeFromByteArray(Property.serializer(), bytes))
    }

    @Test
    fun treeSerializesAllCompoundsWithoutLivePaths() {
        val liveKey = Any()
        val keyValue = PropertyValue.Primitive(PropertyScalar.Str("key"))
        val keyLocation = PropertyLocation(
            PropertyPath(listOf(PropertyPath.Segment.Field("table"), PropertyPath.Segment.EntryKey(liveKey))),
            PropertyPathDto(listOf(PropertyPathDto.Segment.Field("table"), PropertyPathDto.Segment.EntryKey(keyValue))),
        )
        val valueLocation = PropertyLocation(
            PropertyPath(listOf(PropertyPath.Segment.Field("table"), PropertyPath.Segment.EntryValue(liveKey))),
            PropertyPathDto(listOf(PropertyPathDto.Segment.Field("table"), PropertyPathDto.Segment.EntryValue(keyValue))),
        )
        val type = PropertyTypeDescriptorId(1)
        val builder = PropertyTreeBuilder(TestComponentWorld())
        val key = builder.property(keyValue, keyLocation, type)
        val value = builder.property(PropertyValue.Primitive(PropertyScalar.Int(7)), valueLocation, type)
        val recordProperty = builder.property(
            PropertyValue.Primitive(PropertyScalar.Str("record")),
            PropertyLocation().field("record").field("field"),
            type,
        )
        val collectionProperty = builder.property(
            PropertyValue.Primitive(PropertyScalar.Int(8)),
            PropertyLocation().field("collection").index(0),
            type,
        )
        val collides = listOf("wall")
        val compounds = mapOf(
            PropertyReference(1) to PropertyCompound.Record(mapOf("field" to recordProperty)),
            PropertyReference(2) to PropertyCompound.Table(listOf(PropertyCompound.Table.Entry(key, value))),
            PropertyReference(3) to PropertyCompound.Collection(listOf(collectionProperty)),
            PropertyReference(4) to PropertyCompound.PlayerPhysics(true, collides),
            PropertyReference(5) to PropertyCompound.Player("player"),
        )
        builder.compounds.putAll(compounds)
        builder.typeDescriptors[type] = PropertyTypeDescriptor.Scalar(ScalarKind.STRING)
        val serverTree = builder.build()
        val tree = serverTree.tree
        assertSame(recordProperty, (tree.compounds.getValue(PropertyReference(1)) as PropertyCompound.Record).fields.getValue("field"))
        assertSame(liveKey, (serverTree.livePaths.getValue(key.path).segments.last() as PropertyPath.Segment.EntryKey).key)
        assertEquals(recordProperty, (tree.compounds.getValue(PropertyReference(1)) as PropertyCompound.Record).fields["field"])
        assertEquals(
            PropertyCompound.Table(listOf(PropertyCompound.Table.Entry(key, value))),
            tree.compounds[PropertyReference(2)],
        )
        assertEquals(PropertyCompound.Collection(listOf(collectionProperty)), tree.compounds[PropertyReference(3)])
        assertEquals(PropertyCompound.PlayerPhysics(true, listOf("wall")), tree.compounds[PropertyReference(4)])
        assertEquals(PropertyCompound.Player("player"), tree.compounds[PropertyReference(5)])

        val encoded = ProtoBuf.encodeToByteArray(PropertyTree.serializer(), tree)
        assertEquals(tree, ProtoBuf.decodeFromByteArray(PropertyTree.serializer(), encoded))
    }

    @Test
    fun treeKeepsSnapshotOfMutableSourceCollection() {
        val source = mutableListOf(1)
        val builder = PropertyTreeBuilder(TestComponentWorld())
        val root = with(builder) { source.jvmProperty(typeOf<MutableList<Int>>(), PropertyLocation()) }
        val tree = builder.build().tree

        source += 2

        val reference = root.value as PropertyValue.Reference
        val collection = tree.compounds.getValue(reference.objectId) as PropertyCompound.Collection
        assertEquals(listOf(PropertyValue.Primitive(PropertyScalar.Int(1))), collection.values.map { it.value })
    }

    @Test
    fun cyclicReferencesRemainReferencesDuringSerialization() {
        val reference = PropertyReference(1)
        val location = PropertyLocation().field("self")
        val property = Property(PropertyValue.Reference(reference), location.dto, PropertyTypeDescriptorId(1))
        val tree = PropertyTree(
            mapOf(location.dto to property),
            mapOf(reference to PropertyCompound.Record(mapOf("self" to property))),
            emptyMap(),
        )

        val record = tree.compounds.getValue(reference) as PropertyCompound.Record

        assertEquals(PropertyValue.Reference(reference), record.fields.getValue("self").value)
        assertEquals(location.dto, record.fields.getValue("self").path)
        assertEquals(tree, ProtoBuf.decodeFromByteArray(PropertyTree.serializer(), ProtoBuf.encodeToByteArray(PropertyTree.serializer(), tree)))
    }

    @Test
    fun inspectionSnapshotKeepsLivePathsOnTheServer() {
        val liveKey = Any()
        val keyValue = PropertyValue.Primitive(PropertyScalar.Str("key"))
        val location = PropertyLocation().entryValue(liveKey, keyValue)
        val type = PropertyTypeDescriptorId(1)
        val property = Property(PropertyValue.Primitive(PropertyScalar.Int(7)), location.dto, type)
        val tree = PropertyTree(
            properties = mapOf(location.dto to property),
            compounds = mapOf(PropertyReference(3) to PropertyCompound.Record(mapOf("field" to property))),
            types = mapOf(type to PropertyTypeDescriptor.Scalar(ScalarKind.INT)),
        )
        val dto = EntityInspectionSnapshotDto(
            id = EntityId(1, 0),
            clientSide = false,
            snapshotId = "snapshot",
            components = listOf(ComponentInspectionSnapshot("test/component", ComponentInspectionResult.Value(property, tree))),
        )
        val snapshot = EntityInspectionSnapshot(dto, mapOf("test/component" to mapOf(location.dto to location.live)))

        val livePath = snapshot.livePaths.getValue("test/component").getValue(location.dto)
        assertSame(liveKey, (livePath.segments.last() as PropertyPath.Segment.EntryValue).key)
        val encoded = ProtoBuf.encodeToByteArray(EntityInspectionSnapshotDto.serializer(), snapshot.data)
        assertEquals(dto, ProtoBuf.decodeFromByteArray(EntityInspectionSnapshotDto.serializer(), encoded))
    }

    @Test
    fun compoundWireFormatRemainsCompatibleWithPreviousDtoShape() {
        val property = Property(
            PropertyValue.Primitive(PropertyScalar.Str("value")),
            PropertyPathDto(emptyList()),
            PropertyTypeDescriptorId(1),
        )
        val cases = listOf(
            LegacyCompound.Record(mapOf("field" to property)) to PropertyCompound.Record(mapOf("field" to property)),
            LegacyCompound.Table(listOf(LegacyCompound.Table.Entry(property, property))) to
                PropertyCompound.Table(listOf(PropertyCompound.Table.Entry(property, property))),
            LegacyCompound.Collection(listOf(property)) to PropertyCompound.Collection(listOf(property)),
            LegacyCompound.PlayerPhysics(true, listOf("wall")) to PropertyCompound.PlayerPhysics(true, listOf("wall")),
            LegacyCompound.Player("player") to PropertyCompound.Player("player"),
        )

        for ((legacy, current) in cases) {
            val oldBytes = ProtoBuf.encodeToByteArray(LegacyEnvelope.serializer(), LegacyEnvelope(legacy))
            val newBytes = ProtoBuf.encodeToByteArray(CurrentEnvelope.serializer(), CurrentEnvelope(current))
            assertArrayEquals(oldBytes, newBytes)
            assertEquals(CurrentEnvelope(current), ProtoBuf.decodeFromByteArray(CurrentEnvelope.serializer(), oldBytes))
            assertEquals(LegacyEnvelope(legacy), ProtoBuf.decodeFromByteArray(LegacyEnvelope.serializer(), newBytes))
        }
    }

    @Serializable
    private data class LegacyEnvelope(val compound: LegacyCompound)

    @Serializable
    private data class CurrentEnvelope(val compound: PropertyCompound)

    @Serializable
    @SerialName("org.lain.engine.util.property.PropertyCompound")
    private sealed class LegacyCompound {
        open val isEmpty: Boolean = false

        @Serializable
        @SerialName("record")
        data class Record(val fields: Map<String, Property>) : LegacyCompound()

        @Serializable
        @SerialName("table")
        data class Table(val entries: List<Entry>) : LegacyCompound() {
            override val isEmpty: Boolean get() = entries.isEmpty()

            @Serializable
            data class Entry(val key: Property, val value: Property)
        }

        @Serializable
        @SerialName("collection")
        data class Collection(val values: List<Property>) : LegacyCompound() {
            override val isEmpty: Boolean get() = values.isEmpty()
        }

        @Serializable
        @SerialName("player_physics")
        data class PlayerPhysics(val noClip: Boolean, val collides: List<String>) : LegacyCompound()

        @Serializable
        @SerialName("player")
        data class Player(val description: String) : LegacyCompound()
    }
}
