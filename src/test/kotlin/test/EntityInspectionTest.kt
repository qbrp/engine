@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org.lain.engine.test

import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.data.persistentId
import org.lain.engine.player.DeveloperMode
import org.lain.engine.player.PlayerChatHeadsComponent
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.SInt
import org.lain.engine.script.SString
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.ScriptValuePrimitive
import org.lain.engine.script.ComponentInspectionResult
import org.lain.engine.script.EntityInspection
import org.lain.engine.script.EntityInspectionSnapshot
import org.lain.engine.script.EntityInspectionSnapshotDto
import org.lain.engine.script.edit
import org.lain.engine.script.inspectionSnapshot
import org.lain.engine.transport.packet.EntityInspectionValueEditPacket
import org.lain.engine.util.property.PropertyEdit
import org.lain.engine.util.property.PropertyInput
import org.lain.engine.util.property.PropertyPath
import org.lain.engine.util.property.PropertyPathDto
import org.lain.engine.util.property.PropertyReference
import org.lain.engine.util.property.PropertyScalar
import org.lain.engine.util.property.PropertyValue
import org.lain.engine.util.property.ScriptValueBinding

class EntityInspectionTest : EngineTest() {
    @Test
    fun failedComponentDoesNotAffectOtherComponentTree() {
        val world = DummyWorld()
        val entity = world.addEntity()
        val badType = CoreScriptComponents.PLAYER_ATTRIBUTES
        val goodType = CoreScriptComponents.PLAYER_CUSTOM_ATTRIBUTES
        val badValue = object : ScriptValueBinding.Primitive {
            override val value: ScriptValuePrimitive get() = error("test failure")
        }
        val badTable = TestTable(
            listOf(
                ScriptValueBinding.Table.Entry(TestPrimitive(SString("first")), TestPrimitive(SInt(1))),
                ScriptValueBinding.Table.Entry(TestPrimitive(SString("second")), badValue),
            ),
        )
        val goodTable = TestTable(
            listOf(ScriptValueBinding.Table.Entry(TestPrimitive(SString("value")), TestPrimitive(SInt(7))))
        )
        with(world) {
            entity.setComponent(TestScriptComponent(badType, badTable), badType)
            entity.setComponent(TestScriptComponent(goodType, goodTable), goodType)
        }

        val snapshot = with(world) { entity.inspectionSnapshot() }
        val bad = snapshot.data.components.single { it.type == badType.id }
        val good = snapshot.data.components.single { it.type == goodType.id }.data as ComponentInspectionResult.Value
        val key = PropertyValue.Primitive(PropertyScalar.Str("value"))
        val path = PropertyPathDto(listOf(PropertyPathDto.Segment.EntryValue(key)))

        assertTrue(bad.data is ComponentInspectionResult.Error)
        assertNull(snapshot.livePaths[badType.id])
        assertEquals(PropertyValue.Primitive(PropertyScalar.Int(7)), good.tree.properties.getValue(path).value)
        assertTrue(snapshot.livePaths.getValue(goodType.id).containsKey(path))
    }

    @Test
    fun componentTreesUseIndependentPathsAndEditsSelectTheComponent() {
        val world = DummyWorld()
        val entity = world.addEntity()
        val developer = DeveloperMode(false)
        val chatHeads = PlayerChatHeadsComponent(false)
        with(world) {
            entity.setComponent(developer)
            entity.setComponent(chatHeads)
        }

        val snapshot = with(world) { entity.inspectionSnapshot() }
        val developerType = componentTypeOf(DeveloperMode::class).id
        val chatHeadsType = componentTypeOf(PlayerChatHeadsComponent::class).id
        val path = PropertyPathDto(listOf(PropertyPathDto.Segment.Field("enabled")))
        val developerTree = (snapshot.data.components.single { it.type == developerType }.data as ComponentInspectionResult.Value).tree
        val chatHeadsTree = (snapshot.data.components.single { it.type == chatHeadsType }.data as ComponentInspectionResult.Value).tree

        assertEquals(PropertyValue.Primitive(PropertyScalar.Bool(false)), developerTree.properties.getValue(path).value)
        assertEquals(PropertyValue.Primitive(PropertyScalar.Bool(false)), chatHeadsTree.properties.getValue(path).value)

        val inspection = EntityInspection(entity, rate = 20)
        inspection.remember(snapshot)
        val operation = PropertyEdit.Set(PropertyInput.Scalar(PropertyScalar.Bool(true)))
        val packet = EntityInspectionValueEditPacket(
            persistentId("test-entity"), snapshot.data.snapshotId, developerType, path, operation,
        )
        val encoded = ProtoBuf.encodeToByteArray(EntityInspectionValueEditPacket.serializer(), packet)
        assertEquals(packet, ProtoBuf.decodeFromByteArray(EntityInspectionValueEditPacket.serializer(), encoded))

        with(world) { inspection.edit(snapshot.data.snapshotId, developerType, path, operation) }
        assertTrue(developer.enabled)
        assertFalse(chatHeads.enabled)

        with(world) { inspection.edit("expired", chatHeadsType, path, operation) }
        assertFalse(chatHeads.enabled)
        with(world) { inspection.edit(snapshot.data.snapshotId, chatHeadsType, path, operation) }
        assertTrue(chatHeads.enabled)
    }

    @Test
    fun referencesResolveOnlyWithinTheirRetainedSnapshot() {
        val entity = EntityId(1, 0)
        val inspection = EntityInspection(entity, rate = 1)
        val path = PropertyPathDto(
            listOf(PropertyPathDto.Segment.EntryValue(PropertyValue.Reference(PropertyReference(0))))
        )
        val firstKey = Any()
        val secondKey = Any()

        fun snapshot(id: String, key: Any) = EntityInspectionSnapshot(
            EntityInspectionSnapshotDto(entity, false, id, emptyList()),
            mapOf("component" to mapOf(path to PropertyPath(listOf(PropertyPath.Segment.EntryValue(key))))),
        )

        inspection.remember(snapshot("first", firstKey))
        inspection.remember(snapshot("second", secondKey))
        val firstPath = inspection.livePath("first", "component", path)!!
        val secondPath = inspection.livePath("second", "component", path)!!
        assertSame(firstKey, (firstPath.segments.single() as PropertyPath.Segment.EntryValue).key)
        assertSame(secondKey, (secondPath.segments.single() as PropertyPath.Segment.EntryValue).key)
        assertNotEquals(firstPath, secondPath)
        assertNull(inspection.livePath("second", "other-component", path))

        repeat(31) { inspection.remember(snapshot("new-$it", Any())) }
        assertNull(inspection.livePath("first", "component", path))
        assertSame(secondKey, (inspection.livePath("second", "component", path)!!.segments.single() as PropertyPath.Segment.EntryValue).key)
    }

    @Test
    fun editPacketSerializesNestedPropertyInput() {
        val scalar = PropertyInput.Scalar(PropertyScalar.Int(3))
        val input = PropertyInput.Record(
            listOf(
                PropertyInput.Record.Field(
                    "items",
                    PropertyInput.Collection(
                        listOf(PropertyInput.Map(listOf(PropertyInput.Map.Entry(scalar, scalar))))
                    ),
                ),
            ),
        )
        val packet = EntityInspectionValueEditPacket(
            persistentId("test-entity"), "snapshot", "component", PropertyPathDto(emptyList()), PropertyEdit.Set(input),
        )

        val encoded = ProtoBuf.encodeToByteArray(EntityInspectionValueEditPacket.serializer(), packet)
        assertEquals(packet, ProtoBuf.decodeFromByteArray(EntityInspectionValueEditPacket.serializer(), encoded))
    }

    private class TestPrimitive(override val value: ScriptValuePrimitive) : ScriptValueBinding.Primitive

    private class TestTable(override val entries: List<ScriptValueBinding.Table.Entry>) : ScriptValueBinding.Table {
        override fun replace(entry: ScriptValueBinding.Table.Entry, value: ScriptValue) = error("Not used")
        override fun set(key: ScriptValue, value: ScriptValue) = error("Not used")
    }

    private class TestScriptComponent(
        override val type: ScriptComponentType,
        override val inspectionValueNode: ScriptValueBinding,
    ) : ScriptComponent {
        override val value: ScriptValue = SString("test")
    }
}
