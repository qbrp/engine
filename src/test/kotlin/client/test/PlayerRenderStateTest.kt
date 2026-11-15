package org.lain.engine.client.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.client.render.player.DetachedEquipmentNode
import org.lain.engine.client.render.player.PlayerExtraction
import org.lain.engine.client.render.player.PlayerRenderNode
import org.lain.engine.client.render.player.clearPlayerRenderState
import org.lain.engine.client.render.player.extractPlayerRenderState
import org.lain.engine.client.render.scene.RenderScene
import org.lain.engine.client.util.registerSceneComponents
import org.lain.engine.player.EquipmentSlot
import org.lain.engine.player.EquipmentSlotId
import org.lain.engine.player.PlayerPart
import org.lain.engine.script.EngineId
import org.lain.engine.test.EngineTest
import org.lain.engine.test.TestEngineSimulation
import org.lain.engine.util.ecs.ComponentTypeRegistry
import org.lain.engine.world.World
import org.lain.engine.world.WorldId

class PlayerRenderStateTest : EngineTest() {
    @Test
    fun playerWithoutMinecraftEntityDoesNotKeepDeletedRenderNode() {
        val world = World(WorldId("render-test"), TestEngineSimulation(isClient = true))
        val scene = RenderScene(world.simulation.namespacedStorage)
        val root = scene.addEntity()
        val player = with(world) {
            addEntity {
                setComponent(PlayerExtraction(root))
            }
        }

        world.extractPlayerRenderState(scene, null)

        assertFalse(scene.exists(root))
        with(world) { assertNull(player.getComponent<PlayerExtraction>()) }
        val reused = scene.addEntity { setComponent(PlayerRenderNode(eyeLevel = 7)) }
        assertEquals(root.index, reused.index)
        assertTrue(root != reused)

        world.extractPlayerRenderState(scene, null)

        assertTrue(scene.exists(reused))
        with(scene) { assertEquals(7, reused.getComponent<PlayerRenderNode>()!!.eyeLevel) }
    }

    @Test
    fun repeatedExtractionReleasesEquipmentNodesAlongWithThePlayerRoot() {
        val world = World(WorldId("render-test"), TestEngineSimulation(isClient = true))
        val scene = RenderScene(world.simulation.namespacedStorage)
        val player = world.addEntity()
        val slot = EquipmentSlot(EquipmentSlotId(EngineId("test/head")), "Head", part = PlayerPart.HEAD)

        repeat(100) {
            val equipment = scene.addEntity()
            val root = scene.addEntity {
                setComponent(
                    PlayerRenderNode(
                        detachedEquipment = listOf(DetachedEquipmentNode(equipment, slot, false, false))
                    )
                )
            }
            with(world) { player.setComponent(PlayerExtraction(root)) }

            world.extractPlayerRenderState(scene, null)

            assertFalse(scene.exists(root))
            assertFalse(scene.exists(equipment))
            with(world) { assertNull(player.getComponent<PlayerExtraction>()) }
            assertTrue(root.index < 2 && equipment.index < 2, "Render nodes must be reused between extractions")
        }
    }

    @Test
    fun clearingOnePlayerPreservesOtherPlayersAndUnrelatedSceneNodes() {
        val world = World(WorldId("render-test"), TestEngineSimulation(isClient = true))
        val scene = RenderScene(world.simulation.namespacedStorage)
        val firstRoot = scene.addEntity()
        val secondRoot = scene.addEntity()
        val unrelated = scene.addEntity()
        val first = world.addEntity { setComponent(PlayerExtraction(firstRoot)) }
        val second = world.addEntity { setComponent(PlayerExtraction(secondRoot)) }

        with(world) { first.clearPlayerRenderState(scene) }

        assertFalse(scene.exists(firstRoot))
        assertTrue(scene.exists(secondRoot))
        assertTrue(scene.exists(unrelated))
        with(world) {
            assertTrue(exists(first))
            assertNull(first.getComponent<PlayerExtraction>())
            assertEquals(secondRoot, second.getComponent<PlayerExtraction>()!!.node)
        }

        world.clearPlayerRenderState(scene)
        world.clearPlayerRenderState(scene)

        assertFalse(scene.exists(secondRoot))
        assertTrue(scene.exists(unrelated))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerRenderComponents() {
            if (!ComponentTypeRegistry.isRegistered(PlayerExtraction::class)) {
                ComponentTypeRegistry.registerSceneComponents()
            }

        }
    }
}
