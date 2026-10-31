package org.lain.engine.client.test

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.engine.client.render.item.EngineItemDisplayContext
import org.lain.engine.client.render.player.CharacterSceneSnapshot
import org.lain.engine.client.render.player.DetachedEquipment
import org.lain.engine.client.render.player.DetachedEquipmentNode
import org.lain.engine.client.render.player.EyeLevel
import org.lain.engine.client.render.scene.LightSample
import org.lain.engine.client.render.scene.RenderEntityReferences
import org.lain.engine.client.render.scene.RenderSceneSnapshot
import org.lain.engine.client.render.scene.RenderStateSnapshot
import org.lain.engine.client.util.registerSceneComponents
import org.lain.engine.data.polymorphicComponentSerializer
import org.lain.engine.player.EquipmentSlot
import org.lain.engine.player.EquipmentSlotId
import org.lain.engine.player.PlayerPart
import org.lain.engine.script.EngineId
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.test.EngineTest
import org.lain.engine.util.ecs.ComponentTypeRegistry
import org.lain.engine.util.ecs.EntityId

class RenderSceneSnapshotTest : EngineTest() {
    private val storage = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())

    @Test
    fun restoresForwardSharedSelfAndCyclicReferencesBeforeComponentsAreUsed() {
        val snapshot = CharacterSceneSnapshot(
            800,
            RenderSceneSnapshot(
                linkedMapOf(
                    800 to RenderStateSnapshot(listOf(SnapshotTestLinks(listOf(70, 70, 800)), EyeLevel(70))),
                    70 to RenderStateSnapshot(listOf(SnapshotTestLinks(listOf(800, 70)))),
                )
            ),
        )

        val restored = snapshot.revive(storage)
        with(restored.scene) {
            val rootLinks = restored.node.getComponent<SnapshotTestLinks>()!!.targets
            val child = rootLinks[0]
            assertEquals(listOf(child, child, restored.node), rootLinks)
            assertEquals(listOf(restored.node, child), child.getComponent<SnapshotTestLinks>()!!.targets)
            assertEquals(70, restored.node.getComponent<EyeLevel>()!!.value)
        }
        assertEquals(listOf(70, 70, 800), (snapshot.scene.entities.getValue(800).components[0] as SnapshotTestLinks).targets)
    }

    @Test
    fun restoresDetachedEquipmentFromMemoryWithoutChangingTheSnapshot() {
        val snapshot = equipmentSnapshot()
        val restored = snapshot.revive(storage)

        with(restored.scene) {
            val equipment = restored.node.getComponent<DetachedEquipment>()!!
            assertEquals(9, equipment.nodes.single().node.getComponent<LightSample>()!!.block)
            assertEquals(0, equipment.nodes.single().node)
            assertEquals(1, restored.node)
        }
        val originalEquipment = snapshot.scene.entities.getValue(800).components.single() as DetachedEquipment
        assertEquals(70, originalEquipment.nodes.single().node)
    }

    @Test
    fun jsonRoundTripKeepsExistingReferenceFormatAndRestoresEquipment() {
        val snapshot = equipmentSnapshot()
        val json = Json { serializersModule = SerializersModule { polymorphicComponentSerializer() } }
        val encoded = json.encodeToJsonElement(CharacterSceneSnapshot.serializer(), snapshot)

        assertEquals(800, encoded.jsonObject.getValue("character").jsonObject.getValue("id").jsonPrimitive.int)
        val encodedEquipment = encoded.jsonObject.getValue("scene").jsonObject.getValue("entities")
            .jsonObject.getValue("800").jsonObject.getValue("components").jsonArray.single().jsonObject
        val encodedNode = encodedEquipment.getValue("nodes").jsonArray.single().jsonObject.getValue("node")
        assertEquals(70, encodedNode.jsonObject.getValue("id").jsonPrimitive.int)

        val decoded = json.decodeFromJsonElement(CharacterSceneSnapshot.serializer(), encoded)
        val restored = decoded.revive(storage)
        with(restored.scene) {
            val child = restored.node.getComponent<DetachedEquipment>()!!.nodes.single().node
            assertEquals(9, child.getComponent<LightSample>()!!.block)
        }
    }

    @Test
    fun repeatedRestoresHaveIndependentMutableComponentsAndReferences() {
        val snapshot = equipmentSnapshot()
        val first = snapshot.revive(storage)
        val second = snapshot.revive(storage)
        assertNotSame(first.scene, second.scene)

        val firstEquipment = with(first.scene) { first.node.getComponent<DetachedEquipment>()!! }
        val secondEquipment = with(second.scene) { second.node.getComponent<DetachedEquipment>()!! }
        assertNotSame(firstEquipment, secondEquipment)

        val firstLight = with(first.scene) { firstEquipment.nodes.single().node.getComponent<LightSample>()!! }
        val secondLight = with(second.scene) { secondEquipment.nodes.single().node.getComponent<LightSample>()!! }
        firstLight.block = 15
        assertEquals(9, secondLight.block)
        assertEquals(9, (snapshot.scene.entities.getValue(70).components.single() as LightSample).block)
    }

    @Test
    fun missingTargetIsRejectedDuringRestoration() {
        val snapshot = CharacterSceneSnapshot(
            800,
            RenderSceneSnapshot(mapOf(800 to RenderStateSnapshot(listOf(SnapshotTestLinks(listOf(99)))))),
        )
        val failure = assertThrows<IllegalArgumentException> { snapshot.revive(storage) }
        assertTrue(failure.message!!.contains("800 references missing entity 99"))
    }

    @Test
    fun emptyScenesRestoreAndMissingCharacterRootsAreRejected() {
        assertEquals(0, RenderSceneSnapshot.EMPTY.revive(storage).addEntity())
        assertThrows<IllegalArgumentException> {
            CharacterSceneSnapshot(800, RenderSceneSnapshot.EMPTY).revive(storage)
        }
    }

    private fun equipmentSnapshot(): CharacterSceneSnapshot {
        val slot = EquipmentSlot(EquipmentSlotId(EngineId("test/head")), "Head", part = PlayerPart.HEAD)
        return CharacterSceneSnapshot(
            800,
            RenderSceneSnapshot(
                linkedMapOf(
                    70 to RenderStateSnapshot(listOf(LightSample(9))),
                    800 to RenderStateSnapshot(
                        listOf(
                            DetachedEquipment(
                                listOf(DetachedEquipmentNode(70, slot, false, false))
                            )
                        )
                    ),
                )
            ),
        )
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerRenderComponents() {
            if (!ComponentTypeRegistry.isRegistered(DetachedEquipment::class)) {
                ComponentTypeRegistry.registerSceneComponents()
            }
            if (!ComponentTypeRegistry.isRegistered(SnapshotTestLinks::class)) {
                ComponentTypeRegistry.registerComponent<SnapshotTestLinks>(isSavable = true, id = "test/renderlinks")
            }
        }
    }
}

@Serializable
data class SnapshotTestLinks(val targets: List<EntityId>) : RenderEntityReferences {
    override fun remapEntityReferences(remap: (EntityId) -> EntityId): Component =
        copy(targets = targets.map(remap))
}
