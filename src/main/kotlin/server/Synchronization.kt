package org.lain.engine.server

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.player.Player
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.toSnapshotDto
import org.lain.engine.util.component.ComponentTypeRegistry
import org.lain.engine.util.component.EntityId
import org.lain.engine.util.component.IndexedComponentType
import org.lain.engine.world.World
import java.util.*

data class PlayerInstantiationConfirmation(
    var timeout: Int = 8_000
) : Component

@Serializable
object Networked : Component

data class Changes(
    var revision: Long = 0,
    var changed: Boolean = false,
    val updated: BitSet = BitSet(ComponentTypeRegistry.count),
    val removed: BitSet = BitSet(ComponentTypeRegistry.count)
    //TODO: скорее всего сразу после создания BitSet-ов те будут увеличиваться, т.к.
    //после компиляции скриптов общее количество типов компонентов измениться, а здесь
    //же учитываюся только встроенные. Сделать учёт Lua-компонентов
) : Component {
    fun <T : Component> markUpdated(type: IndexedComponentType<T>): Changes {
        changed = true
        removed.clear(type.idx)
        updated.set(type.idx)
        return this
    }

    inline fun <reified T : Component> markUpdated(): Changes =
        markUpdated(ComponentTypeRegistry.componentTypeOf(T::class))

    inline fun <reified T : Component> markRemoved(
        type: IndexedComponentType<T> = ComponentTypeRegistry.componentTypeOf(T::class)
    ): Changes {
        changed = true
        removed.set(type.idx)
        updated.clear(type.idx)
        return this
    }

    fun clear() {
        updated.clear()
        removed.clear()
        changed = false
    }
}

context(world: World)
inline fun <reified T : Component> EntityId.markUpdated() {
    networkState().markUpdated<T>()
}

context(world: World)
inline fun <reified T : Component> EntityId.markRemoved() {
    networkState().markRemoved<T>()
}

context(world: MutableComponentAccess)
fun EntityId.networkState() = getComponent<Changes>() ?: run {
    val state = Changes()
    setComponent(state)
    state
}

inline fun BitSet.forEachIndex(action: (Int) -> Unit) {
    var index = nextSetBit(0)
    while (index >= 0) {
        action(index)
        index = nextSetBit(index + 1)
    }
}

context(world: World)
fun EntityId.collectNetworkedComponents() = world.componentManager.getNetworkedComponents(this)
    .map { component -> component.toSnapshotDto() }

private fun EntityStateFrame.deltaSnapshot() = EntityNetworkSnapshot.Delta(baseRevision, revision, delta)

context(world: World)
private fun EntityId.fullNetworkSnapshot() = EntityNetworkSnapshot.Full(
    networkState().revision,
    collectNetworkedComponents()
)

fun World.sendSnapshots(
    server: EngineServer,
    worldStateFrame: WorldStateFrame,
    handler: ServerHandler
) {
    val world = this
    val entitiesFrame = worldStateFrame.entities
    val fullSnapshotCache = mutableMapOf<PersistentId, EntityNetworkSnapshot.Full>()

    iterate<Player, PlayerSyncState>() { _, (player), state ->
        if (!state.confirmed) return@iterate

        state.freshPlayers.forEach { (playerToSync) ->
            handler.sendFullPlayerState(player, playerToSync)
        }
        state.entities.let { trackState ->
            trackState.fresh.forEach {
                val entity = persistentIdToEntity[it] ?: return@forEach
                val state = fullSnapshotCache.getOrPut(it) { entity.fullNetworkSnapshot() }
                handler.sendEntityState(player, it, entity, state)
            }
            (trackState.synced - trackState.fresh).forEach {
                val snapshot = entitiesFrame[it] ?: return@forEach
                handler.sendEntityState(player, it, snapshot.entity, snapshot.deltaSnapshot())
            }
        }

        if (!state.isWorldSynced) {
            handler.sendWorldState(player, world.state.fullNetworkSnapshot())
            state.isWorldSynced = true
        } else {
            if (worldStateFrame.worldState != null) {
                handler.sendWorldState(player, worldStateFrame.worldState.deltaSnapshot())
            }
        }
    }
}

fun World.tickSynchronizationSystem(server: EngineServer) = runBlocking {
    val globals = server.globals
    val synchronizationRadius = globals.playerSynchronizationRadius
    val desynchronizationRadius = synchronizationRadius + globals.playerDesynchronizationThreshold
    val (job, frame) = componentManager.withoutThreadRestriction {
        withContext(Dispatchers.Default) {
            val job = launch { tickPlayerInterestsSystem(synchronizationRadius) }
            val frame = async { composeStateFrame() }
            job to frame
        }
    }
    job.join()
    tickPlayerTrackingSystem(server, desynchronizationRadius)
    sendSnapshots(server, frame.await(), server.handler)
}