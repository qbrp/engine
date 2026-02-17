package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.engine.mc.BlockHint
import org.lain.engine.storage.loadChunk
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

class ChunkStorage {
    private val chunks = mutableMapOf<Long, EngineChunk>()

    fun getChunks() = chunks.toMap()

    fun setChunk(pos: EngineChunkPos, chunk: EngineChunk) {
        chunks[pos.toLong()] = chunk
    }

    fun removeChunk(pos: EngineChunkPos) {
        chunks.remove(pos.toLong())
    }

    fun getDecals(pos: VoxelPos): BlockDecals? {
        return getChunk(pos)?.decals[pos]
    }

    fun removeVoxel(pos: VoxelPos) {
        val chunk = getChunkByVoxel(pos.x, pos.z) ?: return
        chunk.hints.remove(pos)
        chunk.decals.remove(pos)
    }

    fun getChunk(pos: EngineChunkPos): EngineChunk? = getChunk(pos.x, pos.z)

    fun requireChunk(pos: EngineChunkPos): EngineChunk {
        return getChunk(pos) ?: (loadChunk(pos) ?: EngineChunk())
            .also { setChunk(pos, it) }
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