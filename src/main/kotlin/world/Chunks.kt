package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.iterate
import org.lain.engine.player.EnginePlayer
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerHandler
import org.lain.engine.data.Uuid
import org.lain.engine.util.math.Pos
import org.lain.engine.util.math.floorToInt


data class EngineChunk(
    val decals: MutableMap<ImmutableVoxelPos, BlockDecals> = mutableMapOf(),
    val hints: MutableMap<ImmutableVoxelPos, Hint> = mutableMapOf(),
    val dynamicVoxels: MutableMap<ImmutableVoxelPos, EntityId> = mutableMapOf()
) {
    fun isEmpty() = decals.isEmpty() && hints.isEmpty() && dynamicVoxels.isEmpty()

    fun getOrCreateBlockHint(pos: VoxelPos) = hints.computeIfAbsent(ImmutableVoxelPos(pos)) {
        Hint(
            listOf(),
            Uuid.next()
        )
    }
}

@Serializable
data class EngineChunkPos(val x: Int, val z: Int) {
    fun toLong(): Long = toLong(x, z)
    fun regionX(): Int = this.x shr 5
    fun regionZ(): Int = this.z shr 5

    fun getStartX(): Int {
        return getBlockCoord(this.x)
    }

    fun getStartZ(): Int {
        return getBlockCoord(this.z)
    }

    fun getEndX(): Int {
        return getBlockCoord(this.x) + 15
    }

    fun getEndZ(): Int {
        return getBlockCoord(this.z) + 15
    }

    fun getCenterX(): Int {
        return getBlockCoord(this.x) + 8
    }

    fun getCenterZ(): Int {
        return getBlockCoord(this.z) + 8
    }

    override fun toString(): String {
        return "$x, $z"
    }

    companion object {
        fun toLong(chunkX: Int, chunkZ: Int): Long {
            return chunkX.toLong() and 0xFFFFFFFFL or ((chunkZ.toLong() and 0xFFFFFFFFL) shl 32)
        }
    }
}

fun EngineChunkPos(pos: VoxelPos): EngineChunkPos = EngineChunkPos(chunkSectionCoord(pos.x), chunkSectionCoord(pos.z))

fun EngineChunkPos(pos: Pos): EngineChunkPos = EngineChunkPos(chunkSectionCoord(floorToInt(pos.x)), chunkSectionCoord(floorToInt(pos.z)))

fun chunkSectionCoord(coord: Int): Int {
    return coord shr 4
}

fun getBlockCoord(sectionCoord: Int): Int {
    return sectionCoord shl 4
}

class ChunkStorage(
    private val world: World,
    private val server: EngineServer? = null
) {
    private val chunks = mutableMapOf<Long, EngineChunk>()

    fun setChunk(pos: EngineChunkPos, chunk: EngineChunk) {
        chunks[pos.toLong()] = chunk
    }

    fun removeChunk(pos: EngineChunkPos) {
        chunks.remove(pos.toLong())
    }

    fun getDecals(pos: VoxelPos): BlockDecals? {
        return getChunk(pos)?.decals[pos]
    }

    fun getBlockHint(pos: VoxelPos): Hint? {
        return getChunk(pos)?.hints[pos]
    }

    fun getDynamicVoxel(pos: VoxelPos): EntityId? {
        return getChunk(pos)?.dynamicVoxels[pos]
    }

    fun removeVoxel(pos: VoxelPos): EntityId? {
        val chunk = getChunkByVoxel(pos.x, pos.z) ?: return null
        val hint = chunk.hints.remove(pos)
        chunk.decals.remove(pos)
        return chunk.dynamicVoxels.remove(pos)
            ?.also {
                world.destroy(it)
                world.emitEvent(VoxelDestroyEvent(pos, hint))
            }
    }

    fun getChunk(pos: EngineChunkPos): EngineChunk? = getChunk(pos.x, pos.z)

    fun requireChunk(pos: EngineChunkPos): EngineChunk {
        return requireChunk(pos.x, pos.z)
    }

    fun requireChunk(voxelPos: VoxelPos): EngineChunk {
        return requireChunk(
            chunkSectionCoord(voxelPos.x),
            chunkSectionCoord(voxelPos.z)
        )
    }

    fun requireChunk(x: Int, z: Int): EngineChunk {
        return getChunk(x, z) ?: run {
            val pos = EngineChunkPos(x, z)
            server?.chunkPersistence?.loadChunk(world, pos)
                ?: EngineChunk().also { setChunk(pos, it) }
        }
    }

    fun getChunk(pos: VoxelPos): EngineChunk? = getChunk(chunkSectionCoord(pos.x), chunkSectionCoord(pos.z))

    private fun getChunk(x: Int, z: Int): EngineChunk? = chunks[EngineChunkPos.toLong(x, z)]

    private fun getChunkByVoxel(x: Int, z: Int): EngineChunk? = getChunk(chunkSectionCoord(x), chunkSectionCoord(z))

}

data class VoxelDestroyEvent(
    val pos: VoxelPos,
    val hint: Hint?
) : Component

fun interface EnginePlayersWatchingChunkProvider {
    fun getPlayersWatchingChunk(pos: EngineChunkPos): Collection<EnginePlayer>
}

@Serializable
data class Setter<T>(val value: T?, val remove: Boolean) {
    fun apply(pos: VoxelPos, map: MutableMap<ImmutableVoxelPos, T>) {
        val immutablePos = ImmutableVoxelPos(pos)
        if (remove) map.remove(immutablePos)
        if (value != null) map[immutablePos] = value
    }

    companion object {
        fun <T> Set(value: T) = Setter(value, false)
        fun <T> Remove() = Setter<T>(null, true)
    }
}

@Serializable
sealed class VoxelUpdate {
    @Serializable
    data class AttachDecal(val direction: EDirection, val decal: Decal, val layer: DecalsLayerType) : VoxelUpdate()
    @Serializable
    data class DetachDecal(val layers: List<DecalsLayerType>) : VoxelUpdate()
    @Serializable
    data class AddHint(val text: String) : VoxelUpdate()
    @Serializable
    data class RemoveHint(val index: Int) : VoxelUpdate()
    @Serializable
    data class Set(val decals: Setter<BlockDecals>? = null, val hint: Setter<Hint>? = null) : VoxelUpdate()
}

@Serializable
data class VoxelEvent(val chunkPos: EngineChunkPos, val updates: VoxelUpdate, val selector: Selector) : Component {
    val positions: List<ImmutableVoxelPos> by lazy {
        when(selector) {
            is Selector.Single -> listOf(selector.pos)
            is Selector.Multi -> selector.positions
        }
    }
    @Serializable
    sealed class Selector {
        @Serializable
        data class Single(val pos: ImmutableVoxelPos) : Selector()
        @Serializable
        data class Multi(val positions: List<ImmutableVoxelPos>) : Selector()
    }
}

fun World.voxelEvent(chunkPos: EngineChunkPos, updates: VoxelUpdate, selector: VoxelEvent.Selector) {
    emitEvent(VoxelEvent(chunkPos, updates, selector))
}

fun World.singleBlockVoxelEvent(voxelPos: VoxelPos, updates: VoxelUpdate) {
    voxelEvent(EngineChunkPos(voxelPos), updates, VoxelEvent.Selector.Single(ImmutableVoxelPos(voxelPos)))
}

fun World.updateVoxelEvents(handler: ServerHandler?) = iterate<VoxelEvent> { _, event ->
    val chunkPos = event.chunkPos
    val chunk = chunkStorage.requireChunk(chunkPos)
    val positions = event.positions
    val chunkDecals = chunk.decals
    val chunkHints = chunk.hints
    positions.forEach { voxelPos ->
        when (val update = event.updates) {
            is VoxelUpdate.AttachDecal -> {
                val decals = chunkDecals[voxelPos] ?: BlockDecals.withLayer(update.layer)
                val newDecals = decals.withDecalAtLayer(update.layer, update.direction, update.decal)
                chunkDecals[voxelPos] = newDecals
            }
            is VoxelUpdate.DetachDecal -> {
                val decals = chunkDecals[voxelPos] ?: return@forEach
                val newDecals = decals.withoutLayers(update.layers)
                if (newDecals.isEmpty()) {
                    chunkDecals.remove(voxelPos)
                } else {
                    chunkDecals[voxelPos] = newDecals
                }
            }
            is VoxelUpdate.AddHint -> {
                val hint = chunk.getOrCreateBlockHint(voxelPos)
                val newHint = hint.withText(update.text)
                chunkHints[voxelPos] = newHint
            }
            is VoxelUpdate.RemoveHint -> {
                val hint = chunk.getOrCreateBlockHint(voxelPos)
                val newHint = hint.without(update.index)
                if (newHint.texts.isEmpty()) {
                    chunkHints.remove(voxelPos)
                    emitEvent(HintDestroyEvent(newHint.uuid))
                } else {
                    chunkHints[voxelPos] = newHint
                }
            }
            is VoxelUpdate.Set -> {
                update.decals?.apply(voxelPos, chunkDecals)
                update.hint?.apply(voxelPos, chunkHints)
            }
        }
    }

    handler?.onVoxelEvent(
        this@updateVoxelEvents,
        event,
        playersWatchingChunkProvider!!.getPlayersWatchingChunk(chunkPos)
    )
}
