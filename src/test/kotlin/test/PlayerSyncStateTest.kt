package org.lain.engine.test

import org.lain.engine.player.interaction.InputAction
import org.lain.engine.server.replication.PlayerSyncState
import org.lain.engine.server.replication.ReplicationFrame
import org.lain.engine.server.replication.TrackingState
import org.lain.engine.data.persistentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerSyncStateTest : EngineTest() {
    @Test
    fun outOnlyReplicationFrameIsNotEmpty() {
        val entity = persistentId("entity")

        assertFalse(ReplicationFrame(world = null, out = setOf(entity)).isEmpty())
    }

    @Test
    fun residentEntityIsNotRemovedWhenItBecomesDormant() {
        val state = TrackingState()
        val player = persistentId("player")

        state.update(setOf(player), setOf(player))
        state.update(emptySet(), setOf(player))

        assertTrue(state.synced.isEmpty())
        assertTrue(state.fresh.isEmpty())
        assertEquals(setOf(player), state.resident)
        assertTrue((state.previousTickResident - state.resident).isEmpty())
    }

    @Test
    fun dormantEntityGetsFullSnapshotWhenItBecomesActiveAgain() {
        val state = TrackingState()
        val player = persistentId("player")

        state.update(setOf(player), setOf(player))
        state.update(emptySet(), setOf(player))
        state.update(setOf(player), setOf(player))

        assertEquals(setOf(player), state.fresh)
        assertEquals(setOf(player), state.synced)
    }

    @Test
    fun entityIsRemovedOnlyWhenItStopsBeingResident() {
        val state = TrackingState()
        val player = persistentId("player")

        state.update(setOf(player), setOf(player))
        state.update(emptySet(), emptySet())

        assertEquals(setOf(player), state.previousTickResident - state.resident)
    }

    @Test
    fun inputTransitionsAreQueuedInTickOrderAndCopied() {
        val state = PlayerSyncState()
        val actions = mutableSetOf<InputAction>(InputAction.Attack)

        assertTrue(state.enqueueInput(12, actions))
        actions.clear()
        assertTrue(state.enqueueInput(15, setOf(InputAction.Base)))

        assertEquals(setOf(InputAction.Attack), state.pendingInputs.pollFirstEntry().value)
        assertEquals(15, state.pendingInputs.firstKey())
    }

    @Test
    fun duplicateOrOlderInputTickIsIgnored() {
        val state = PlayerSyncState()

        assertTrue(state.enqueueInput(8, setOf(InputAction.Attack)))
        assertFalse(state.enqueueInput(8, setOf(InputAction.Base)))
        assertFalse(state.enqueueInput(7, setOf(InputAction.Base)))
        assertEquals(setOf(8L), state.pendingInputs.keys)
    }

    @Test
    fun newestInputIsRetainedWhenQueueIsFull() {
        val state = PlayerSyncState()
        repeat(256) { tick ->
            assertTrue(state.enqueueInput(tick.toLong(), emptySet()))
        }

        assertTrue(state.enqueueInput(256, setOf(InputAction.Attack)))

        assertEquals(256, state.pendingInputs.size)
        assertFalse(state.pendingInputs.containsKey(255))
        assertEquals(setOf(InputAction.Attack), state.pendingInputs[256])
    }
}
