package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.engine.player.EnginePlayer
import org.lain.engine.server.ServerHandler
import org.lain.engine.storage.loadChunk
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.engine.util.injectEngineServer
import org.lain.engine.util.math.Pos
import org.lain.engine.util.math.floorToInt
import org.slf4j.LoggerFactory

/**
 * Дополнительные данные чанка
 */
@Serializable
data class EngineChunk(
    val decals: MutableMap<VoxelPos, BlockDecals> = mutableMapOf(),
    val hints: MutableMap<VoxelPos, BlockHint> = mutableMapOf(),
) {
    fun isEmpty() = decals.isEmpty() && hints.isEmpty()
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

class ChunkStorage(private val world: World) {
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

    fun getBlockHint(pos: VoxelPos): BlockHint? {
        return getChunk(pos)?.hints[pos]
    }

    fun removeVoxel(pos: VoxelPos) {
        val chunk = getChunkByVoxel(pos.x, pos.z) ?: return
        chunk.hints.remove(pos)
        chunk.decals.remove(pos)
    }

    fun getChunk(pos: EngineChunkPos): EngineChunk? = getChunk(pos.x, pos.z)

    fun requireChunk(pos: EngineChunkPos): EngineChunk {
        return getChunk(pos) ?: run {
            val loadedChunk = loadChunk(pos) ?: EngineChunk()
            setChunk(pos, loadedChunk)
            loadedChunk
        }
    }

    private fun loadChunk(pos: EngineChunkPos): EngineChunk? {
        val server by injectEngineServer()
        return try {
            loadChunk(server, pos)?.let {
                EngineChunk(
                    it.decals.toMutableMap(),
                    it.hints.toMutableMap()
                )
            }
        } catch (e: Throwable) {
            LOGGER.error("Ошибка загрузки чанка $pos", e)
            null
        }
    }

    private fun getChunk(pos: VoxelPos): EngineChunk? = getChunk(chunkSectionCoord(pos.x), chunkSectionCoord(pos.z))

    private fun getChunk(x: Int, z: Int): EngineChunk? = chunks[EngineChunkPos.toLong(x, z)]

    private fun getChunkByVoxel(x: Int, z: Int): EngineChunk? = getChunk(chunkSectionCoord(x), chunkSectionCoord(z))

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Engine Chunks")
    }
}

fun interface EnginePlayersWatchingChunkProvider  {
    fun getPlayersWatchingChunk(pos: EngineChunkPos): Collection<EnginePlayer>
}

@Serializable
data class Setter<T>(val value: T?, val remove: Boolean) {
    fun apply(pos: VoxelPos, map: MutableMap<VoxelPos, T>) {
        if (remove) map.remove(pos)
        if (value != null) map[pos] = value
    }

    companion object {
        fun <T> Set(value: T) = Setter(value, false)
        fun <T> Remove() = Setter<T>(null, true)
    }
}

@Serializable
sealed class VoxelUpdate {
    @Serializable
    data class AttachDecal(val direction: Direction, val decal: Decal, val layer: DecalsLayerType) : VoxelUpdate()
    @Serializable
    data class DetachDecal(val layers: List<DecalsLayerType>) : VoxelUpdate()
    @Serializable
    data class AddHint(val text: String) : VoxelUpdate()
    @Serializable
    data class RemoveHint(val index: Int) : VoxelUpdate()
    @Serializable
    data class Set(val decals: Setter<BlockDecals>? = null, val hint: Setter<BlockHint>? = null) : VoxelUpdate()
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
                val hint = chunkHints[voxelPos] ?: return@forEach
                val newHint = hint.withText(update.text)
                chunkHints[voxelPos] = newHint
            }
            is VoxelUpdate.RemoveHint -> {
                val hint = chunkHints[voxelPos] ?: return@forEach
                val newHint = hint.without(update.index)
                if (newHint.texts.isEmpty()) {
                    chunkHints.remove(voxelPos)
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