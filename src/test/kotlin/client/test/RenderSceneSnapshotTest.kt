package org.lain.engine.client.test

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import net.minecraft.resources.ResourceLocation
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
import org.lain.engine.client.render.player.DetachedEquipmentNode
import org.lain.engine.client.render.player.PlayerRenderNode
import org.lain.engine.client.render.scene.LightSample
import org.lain.engine.client.render.scene.RenderEntityReferences
import org.lain.engine.client.render.scene.RenderSceneSnapshot
import org.lain.engine.client.render.scene.RenderStateSnapshot
import org.lain.engine.client.util.registerSceneComponents
import org.lain.engine.data.polymorphicComponentSerializer
import org.lain.engine.player.ArmPose
import org.lain.engine.player.EquipmentSlot
import org.lain.engine.player.EquipmentSlotId
import org.lain.engine.player.PlayerPart
import org.lain.engine.script.EngineId
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.test.EngineTest
import org.lain.engine.util.ecs.ComponentTypeRegistry
import org.lain.cyberia.ecs.EntityId

class RenderSceneSnapshotTest : EngineTest() {
    private val storage = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())

    @Test
    fun restoresForwardSharedSelfAndCyclicReferencesBeforeComponentsAreUsed() {
        val snapshot = CharacterSceneSnapshot(
            EntityId(800, 0),
            RenderSceneSnapshot(
                linkedMapOf(
                    EntityId(800, 0) to RenderStateSnapshot(
                        listOf(SnapshotTestLinks(listOf(EntityId(70, 0), EntityId(70, 0), EntityId(800, 0))), PlayerRenderNode(eyeLevel = 70))
                    ),
                    EntityId(70, 0) to RenderStateSnapshot(listOf(SnapshotTestLinks(listOf(EntityId(800, 0), EntityId(70, 0))))),
                )
            ),
        )

        val restored = snapshot.revive(storage)
        with(restored.scene) {
            val rootLinks = restored.node.getComponent<SnapshotTestLinks>()!!.targets
            val child = rootLinks[0]
            assertEquals(listOf(child, child, restored.node), rootLinks)
            assertEquals(listOf(restored.node, child), child.getComponent<SnapshotTestLinks>()!!.targets)
            assertEquals(70, restored.node.getComponent<PlayerRenderNode>()!!.eyeLevel)
        }
        assertEquals(listOf(EntityId(70, 0), EntityId(70, 0), EntityId(800, 0)), (snapshot.scene.entities.getValue(EntityId(800, 0)).components[0] as SnapshotTestLinks).targets)
    }

    @Test
    fun restoresDetachedEquipmentFromMemoryWithoutChangingTheSnapshot() {
        val snapshot = equipmentSnapshot()
        val restored = snapshot.revive(storage)

        with(restored.scene) {
            val renderNode = restored.node.getComponent<PlayerRenderNode>()!!
            val equipment = renderNode.detachedEquipment
            assertEquals(ArmPose.EXPOSE, renderNode.mainArmPose)
            assertEquals(ArmPose.HOLD_WEAPON, renderNode.offArmPose)
            assertEquals(5, renderNode.eyeLevel)
            assertEquals(9, equipment.single().node.getComponent<LightSample>()!!.block)
            assertEquals(0, equipment.single().node.index)
            assertEquals(1, restored.node.index)
        }
        val originalEquipment =
            (snapshot.scene.entities.getValue(EntityId(800, 0)).components.single() as PlayerRenderNode).detachedEquipment
        assertEquals(EntityId(70, 0), originalEquipment.single().node)
    }

    @Test
    fun jsonRoundTripPreservesReferencesAndRestoresEquipment() {
        val snapshot = equipmentSnapshot()
        val json = Json {
            allowStructuredMapKeys = true
            serializersModule = SerializersModule { polymorphicComponentSerializer() }
        }
        val encoded = json.encodeToJsonElement(CharacterSceneSnapshot.serializer(), snapshot)

        val decoded = json.decodeFromJsonElement(CharacterSceneSnapshot.serializer(), encoded)
        assertEquals(snapshot, decoded)
        val restored = decoded.revive(storage)
        with(restored.scene) {
            val child = restored.node.getComponent<PlayerRenderNode>()!!.detachedEquipment.single().node
            assertEquals(9, child.getComponent<LightSample>()!!.block)
        }
    }

    @Test
    fun playerSkinTextureUsesResourceLocationStringInJson() {
        val skin = ResourceLocation.parse("example:textures/entity/skin.png")
        val node = PlayerRenderNode(skinTexture = skin)
        val encoded = Json.encodeToJsonElement(PlayerRenderNode.serializer(), node)

        assertEquals("example:textures/entity/skin.png", encoded.jsonObject.getValue("skinTexture").jsonPrimitive.content)
        assertEquals(node, Json.decodeFromJsonElement(PlayerRenderNode.serializer(), encoded))
        assertEquals(
            skin,
            Json.decodeFromString(PlayerRenderNode.serializer(), """{"skinTexture":"example:textures/entity/skin.png"}""").skinTexture,
        )
        assertThrows<SerializationException> {
            Json.decodeFromString(PlayerRenderNode.serializer(), """{"skinTexture":"Bad Namespace:skin"}""")
        }
    }

    @Test
    fun repeatedRestoresHaveIndependentMutableComponentsAndReferences() {
        val snapshot = equipmentSnapshot()
        val first = snapshot.revive(storage)
        val second = snapshot.revive(storage)
        assertNotSame(first.scene, second.scene)

        val firstEquipment = with(first.scene) { first.node.getComponent<PlayerRenderNode>()!! }
        val secondEquipment = with(second.scene) { second.node.getComponent<PlayerRenderNode>()!! }
        assertNotSame(firstEquipment, secondEquipment)

        val firstLight = with(first.scene) { firstEquipment.detachedEquipment.single().node.getComponent<LightSample>()!! }
        val secondLight = with(second.scene) { secondEquipment.detachedEquipment.single().node.getComponent<LightSample>()!! }
        firstLight.block = 15
        assertEquals(9, secondLight.block)
        assertEquals(9, (snapshot.scene.entities.getValue(EntityId(70, 0)).components.single() as LightSample).block)
    }

    @Test
    fun missingTargetIsRejectedDuringRestoration() {
        val snapshot = CharacterSceneSnapshot(
            EntityId(800, 0),
            RenderSceneSnapshot(mapOf(EntityId(800, 0) to RenderStateSnapshot(listOf(SnapshotTestLinks(listOf(EntityId(99, 0))))))),
        )
        val failure = assertThrows<IllegalArgumentException> { snapshot.revive(storage) }
        assertTrue(failure.message!!.contains("references missing entity"))
    }

    @Test
    fun emptyScenesRestoreAndMissingCharacterRootsAreRejected() {
        assertEquals(0, RenderSceneSnapshot.EMPTY.revive(storage).addEntity().index)
        assertThrows<IllegalArgumentException> {
            CharacterSceneSnapshot(EntityId(800, 0), RenderSceneSnapshot.EMPTY).revive(storage)
        }
    }

    private fun equipmentSnapshot(): CharacterSceneSnapshot {
        val slot = EquipmentSlot(EquipmentSlotId(EngineId("test/head")), "Head", part = PlayerPart.HEAD)
        return CharacterSceneSnapshot(
            EntityId(800, 0),
            RenderSceneSnapshot(
                linkedMapOf(
                    EntityId(70, 0) to RenderStateSnapshot(listOf(LightSample(9))),
                    EntityId(800, 0) to RenderStateSnapshot(
                        listOf(
                            PlayerRenderNode(
                                mainArmPose = ArmPose.EXPOSE,
                                offArmPose = ArmPose.HOLD_WEAPON,
                                detachedEquipment = listOf(
                                    DetachedEquipmentNode(EntityId(70, 0), slot, false, false)
                                ),
                                eyeLevel = 5,
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
            if (!ComponentTypeRegistry.isRegistered(PlayerRenderNode::class)) {
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
