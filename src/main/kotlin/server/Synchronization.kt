package org.lain.engine.server

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.require
import org.lain.engine.storage.ComponentDto
import org.lain.engine.storage.PersistentId
import org.lain.engine.util.component.ComponentTypeRegistry
import org.lain.engine.util.component.ComponentWorld
import org.lain.engine.util.component.EntityId
import org.lain.engine.util.component.IndexedComponentType
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.ImmutableVoxelPos
import org.lain.engine.world.World
import java.util.BitSet

data class PlayerNetworkState(
    var authorized: Boolean,
    val players: MutableList<EnginePlayer> = mutableListOf(),
    val chunks: MutableList<EngineChunkPos> = mutableListOf(),
    var tickTimeout: Int = 8_000,
    val entities: MutableSet<PersistentId> = mutableSetOf(),
    val voxels: MutableSet<ImmutableVoxelPos> = mutableSetOf(),
    var worldSynced: Boolean = false,
) : Component

val EnginePlayer.network
    get() = this.require<PlayerNetworkState>()

@Serializable
object Networked : Component

data class NetworkState(
    val updated: BitSet = BitSet(ComponentTypeRegistry.count),
    val removed: BitSet = BitSet(ComponentTypeRegistry.count)
    //TODO: скорее всего сразу после создания BitSet-ов те будут увеличиваться, т.к.
    //после компиляции скриптов общее количество типов компонентов измениться, а здесь
    //же учитываюся только встроенные. Сделать учёт Lua-компонентов
) : Component {
    inline fun <reified T : Component> markUpdated(
        type: IndexedComponentType<T> = ComponentTypeRegistry.componentTypeOf(T::class)
    ): NetworkState {
        removed.clear(type.idx)
        updated.set(type.idx)
        return this
    }

    inline fun <reified T : Component> markRemoved(
        type: IndexedComponentType<T> = ComponentTypeRegistry.componentTypeOf(T::class)
    ): NetworkState {
        removed.set(type.idx)
        updated.clear(type.idx)
        return this
    }

    fun clear() {
        updated.clear()
        removed.clear()
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

context(world: World)
fun EntityId.networkState() = getComponent<NetworkState>() ?: run {
    val state = NetworkState()
    setComponent(state)
    state
}

@Serializable
data class EntityNetworkSnapshot(
    val updated: List<ComponentDto>,
    val removed: List<String> // type ids
)

fun ComponentWorld.fillNetworkSnapshot(
    updated: MutableList<Component>,
    removed: MutableList<ComponentType<*>>,
    entityId: EntityId
) {
    getDirtyNetworkedComponentsLegacy(updated, entityId)
    networkStateArray.componentOf(entityId)?.let {
        it.updated.forEachIndex { arrayId ->
            getComponentArray(arrayId).componentOf(entityId)
                ?.let { component -> updated += component }
        }
        it.removed.forEachIndex { arrayId ->
            removed += getComponentArray(arrayId).type
        }
    }
}

private inline fun BitSet.forEachIndex(action: (Int) -> Unit) {
    var index = nextSetBit(0)
    while (index >= 0) {
        action(index)
        index = nextSetBit(index + 1)
    }
}