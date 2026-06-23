package org.lain.engine.client

import org.lain.cyberia.ecs.iterate
import org.lain.engine.storage.Uuid
import org.lain.engine.util.addIfNotNull
import org.lain.engine.world.HintDestroyEvent
import org.lain.engine.world.VoxelDestroyEvent
import org.lain.engine.world.VoxelEvent
import org.lain.engine.world.VoxelUpdate

enum class HintState {
    NOT_READ, CHANGED, READ
}

class ClientHintState(val states: MutableMap<Uuid, HintState> = mutableMapOf()) {
    fun stateOf(uuid: Uuid): HintState {
        return states.getOrDefault(uuid, HintState.NOT_READ)
    }

    fun markRead(uuid: Uuid) {
        states[uuid] = HintState.READ
    }

    fun markChanged(uuid: Uuid) {
        val state = when(states.containsKey(uuid)) {
            true -> HintState.CHANGED
            false -> HintState.NOT_READ
        }
        states[uuid] = state
    }
}

/**
@see Chunks
 */
fun GameSession.handleHintEvents() {
    world.iterate<VoxelEvent> { e, voxelEvent ->
        val updates = voxelEvent.updates
        val positions = voxelEvent.positions
        if (updates is VoxelUpdate.AddHint) {
            positions.forEach { pos ->
                hintState.markChanged(
                    world.chunkStorage.getBlockHint(pos)!!.uuid
                )
            }
        }
    }

    val toDestroy = mutableSetOf<Uuid>()
    world.iterate<VoxelDestroyEvent> { e, (pos, hint) ->
        toDestroy.addIfNotNull(hint?.uuid)
    }
    world.iterate<HintDestroyEvent> { e, hint ->
        toDestroy.add(hint.uuid)
    }

    toDestroy.forEach { hintState.states.remove(it) }
}