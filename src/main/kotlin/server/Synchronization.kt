package org.lain.engine.server

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.data.PersistentId
import org.lain.engine.player.PlayerComponent
import org.lain.engine.util.ecs.ComponentTypeRegistry
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.ecs.IndexedComponentType
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
    fun markUpdated(type: IndexedComponentType<out Component>): Changes {
        changed = true
        removed.clear(type.idx)
        updated.set(type.idx)
        return this
    }

    inline fun <reified T : Component> markUpdated(): Changes =
        markUpdated(ComponentTypeRegistry.componentTypeOf(T::class))

    fun markRemoved(type: IndexedComponentType<out Component>): Changes {
        changed = true
        removed.set(type.idx)
        updated.clear(type.idx)
        return this
    }

    inline fun <reified T : Component> markRemoved(): Changes =
        markRemoved(ComponentTypeRegistry.componentTypeOf(T::class))

    fun clear() {
        updated.clear()
        removed.clear()
        changed = false
    }
}

context(world: World)
inline fun <reified T : Component> EntityId.markUpdated() {
    world.componentManager.markDirty(this, ComponentTypeRegistry.componentTypeOf(T::class))
}

context(world: World)
inline fun <reified T : Component> EntityId.markRemoved() {
    val type = ComponentTypeRegistry.componentTypeOf(T::class)
    networkState().markRemoved(type)
    world.componentManager.networkedComponentChangeListener?.invoke(this, type)
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
    .map { component -> component.replicationSnapshot() }

private fun NetworkStateFrame.deltaSnapshot() = EntityNetworkSnapshot.Delta(baseRevision, revision, delta)

context(world: World)
fun EntityId.fullNetworkSnapshot() = EntityNetworkSnapshot.Full(
    networkState().revision,
    collectNetworkedComponents()
)

fun World.sendSnapshots(
    worldStateFrame: WorldStateFrame,
    handler: ServerHandler
) {
    val world = this
    val entitiesFrame = worldStateFrame.entities
    val fullSnapshotCache = mutableMapOf<PersistentId, EntityNetworkSnapshot.Full>()

    iterate<PlayerComponent, PlayerSyncState>() { _, (player), state ->
        if (!state.confirmed) return@iterate
        val entities = mutableMapOf<PersistentId, EntityNetworkSnapshot>()

        state.freshPlayers.forEach { (playerToSync) ->
            handler.sendFullPlayerState(player, playerToSync)
        }
        state.entities.let { trackState ->
            trackState.fresh.forEach {
                val entity = persistentIdToEntity[it] ?: return@forEach
                val state = fullSnapshotCache.getOrPut(it) { entity.fullNetworkSnapshot() }
                entities[it] = state
            }
            (trackState.synced - trackState.fresh).forEach {
                val snapshot = entitiesFrame[it] ?: return@forEach
                entities[it] = snapshot.deltaSnapshot()
            }
        }

        val worldSnapshot = if (!state.isWorldSynced) {
            state.isWorldSynced = true
            world.state.fullNetworkSnapshot()
        } else {
            worldStateFrame.worldState?.deltaSnapshot()
        }

        val processedInputTick = if (state.processedInputTick > state.lastSentProcessedInputTick) {
            state.lastSentProcessedInputTick = state.processedInputTick
            state.processedInputTick
        } else {
            null
        }

        handler.sendReplicationFrame(
            player,
            ReplicationFrameSnapshot(worldSnapshot, entities, processedInputTick),
        )
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
    sendSnapshots(frame.await(), server.handler)
}
