package org.lain.engine.client.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.getComponent
import org.lain.engine.client.handler.unloadReplicatedEntity
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.player.DisplayName
import org.lain.engine.player.PlayerInstantiateSettings
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.Username
import org.lain.engine.player.commonPlayerInstance
import org.lain.engine.player.mainContainer
import org.lain.engine.player.prepareContainers
import org.lain.engine.player.randomPlayerId
import org.lain.engine.player.require
import org.lain.engine.test.EngineTest
import org.lain.engine.test.TestEngineSimulation
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.lain.engine.world.location

class ClientReplicationUnloadingTest : EngineTest() {
    @Test
    fun playerAndOwnedEntitiesCanLeaveInAnyOrder() {
        for (playerFirst in listOf(true, false)) {
            val simulation = TestEngineSimulation(isClient = true)
            val world = World(WorldId("replication-test"), simulation)
            val player = with(world) {
                commonPlayerInstance(
                    PlayerInstantiateSettings(
                        world, Vec3(0, 0, 0), DisplayName(Username("remote")),
                        developerModeStatus = DeveloperModeStatus(),
                    ),
                    randomPlayerId(),
                ).also { it.prepareContainers(it.location) }
            }
            simulation.instantiatePlayer(player)
            val inventory = player.require<PlayerInventory>()
            val children = listOf(player.mainContainer, inventory.mainHandInteractor, inventory.offHandInteractor)
            val ids = with(world) {
                (children + player.entity).associateWith { it.getComponent<PersistentIdComponent>()!!.id }
            }
            val order = if (playerFirst) listOf(player.entity) + children else children + player.entity
            var removedPlayers = 0

            for (entity in order) {
                val live = world.persistentIdToEntity[ids.getValue(entity)] ?: continue
                with(world) {
                    unloadReplicatedEntity(live) { removed ->
                        removedPlayers++
                        simulation.preparePlayerDestroy(removed)
                        simulation.destroyPlayer(removed)
                    }
                }
            }

            assertEquals(1, removedPlayers)
            assertNull(simulation.players.get(player.id))
            assertTrue(ids.values.none(world.persistentIdToEntity::containsKey))
            assertTrue(ids.keys.none(world::exists))
        }
    }

    @Test
    fun removingPlayerDoesNotDestroyAnEntityReusingItsContainersIndex() {
        val simulation = TestEngineSimulation(isClient = true)
        val world = World(WorldId("replication-reuse-test"), simulation)
        val player = with(world) {
            commonPlayerInstance(
                PlayerInstantiateSettings(
                    world, Vec3(0, 0, 0), DisplayName(Username("remote")),
                    developerModeStatus = DeveloperModeStatus(),
                ),
                randomPlayerId(),
            ).also { it.prepareContainers(it.location) }
        }
        simulation.instantiatePlayer(player)
        val container = player.mainContainer
        world.destroy(container)
        val replacement = world.addEntity()

        assertEquals(container.index, replacement.index)
        assertNotEquals(container, replacement)
        with(world) {
            unloadReplicatedEntity(player.entity) { removed ->
                simulation.preparePlayerDestroy(removed)
                simulation.destroyPlayer(removed)
            }
        }

        assertTrue(world.exists(replacement))
        assertFalse(world.exists(player.entity))
    }
}
