@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org.lain.engine.test

import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.data.persistentId
import org.lain.engine.player.DisplayName
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.Username
import org.lain.engine.script.ComponentInspectionSnapshot
import org.lain.engine.script.ComponentInspectionResult
import org.lain.engine.script.EntityInspectionSnapshotDto
import org.lain.engine.util.property.PropertyCompound
import org.lain.engine.util.property.PropertyLocation
import org.lain.engine.util.property.PropertyTreeBuilder
import org.lain.engine.util.property.PropertyTypeDescriptor
import org.lain.engine.util.property.PropertyValue
import org.lain.engine.util.property.ScriptValueBinding
import org.lain.engine.util.property.jvmProperty
import org.lain.engine.util.property.inspectScriptValue
import org.lain.engine.transport.packet.EntityDebugDataPacket
import java.util.UUID
import kotlin.reflect.typeOf

class InspectionPlayerSerializationTest : EngineTest() {
    @Test
    fun playerObjectIsSharedByJvmAndScriptReferences() {
        val player = createPlayer()
        val context = PropertyTreeBuilder(player.world.componentManager)
        val node = with(context) { player.jvmProperty(typeOf<EnginePlayer>(), PropertyLocation().field("jvm")) }
        val scriptNode = with(context) { ScriptJvmBinding(player).inspectScriptValue(PropertyLocation().field("script")) }
        val reference = node.value as PropertyValue.Reference
        val inspected = context.compounds.getValue(reference.objectId) as PropertyCompound.Player

        assertEquals(player.toString(), inspected.description)
        assertFalse(inspected.isEmpty)
        assertEquals(reference, scriptNode.value)
        assertEquals(1, context.compounds.size)
        assertEquals(PropertyTypeDescriptor.Unknown, context.typeDescriptors[node.type])
    }

    @Test
    fun playerInspectionSurvivesTheDebugPacketRoundTrip() {
        val player = createPlayer()
        val context = PropertyTreeBuilder(player.world.componentManager)
        val node = with(context) { player.jvmProperty(typeOf<EnginePlayer>(), PropertyLocation()) }
        val tree = context.build().tree
        val snapshot = EntityInspectionSnapshotDto(
            id = player.entity,
            clientSide = false,
            snapshotId = "snapshot",
            components = listOf(ComponentInspectionSnapshot("test/player", ComponentInspectionResult.Value(node, tree))),
        )
        val packet = EntityDebugDataPacket(persistentId("test-player"), snapshot)

        val encoded = ProtoBuf.encodeToByteArray(EntityDebugDataPacket.serializer(), packet)
        val decoded = ProtoBuf.decodeFromByteArray(EntityDebugDataPacket.serializer(), encoded)

        assertEquals(packet, decoded)
    }

    private fun createPlayer(): EnginePlayer {
        val world = DummyWorld()
        val entity = world.addEntity()
        with(world) { entity.setComponent(DisplayName(Username("observer"))) }
        return EnginePlayer(PlayerId(UUID.fromString("00000000-0000-0000-0000-000000000001")), entity, world)
    }

    private data class ScriptJvmBinding(override val value: Any) : ScriptValueBinding.Jvm
}
