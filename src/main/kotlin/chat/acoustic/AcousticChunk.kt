package org.lain.engine.chat.acoustic

import kotlin.math.ceil

fun splitIntoChunks(size: SceneSize, chunkSize: Int): List<AcousticChunk> {
    val chunks = mutableListOf<AcousticChunk>()
    for (z in 0 until size.depth step chunkSize)
        for (y in 0 until size.height step chunkSize)
            for (x in 0 until size.width step chunkSize) {
                chunks += AcousticChunk(
                    false,
                    0,
                    Grid3Range(
                        x, minOf(x + chunkSize, size.width),
                        y, minOf(y + chunkSize, size.height),
                        z, minOf(z + chunkSize, size.depth)
                    )
                )
            }
    return chunks
}

typealias ChunkMap = Grid3<AcousticChunk>

fun chunkMapOf(size: SceneSize, chunks: List<AcousticChunk>, chunkSize: Int): ChunkMap {
    val w = ceil(size.width.toFloat() / chunkSize).toInt()
    val h = ceil(size.height.toFloat() / chunkSize).toInt()
    val d = ceil(size.depth.toFloat() / chunkSize).toInt()
    return GenericGrid3(w, h, d, chunks.toTypedArray())
}

data class AcousticChunk(var isActive: Boolean, var raised: Int, val grid: Grid3Range) {
    val isRaiseFinished get() = raised >= (grid.x1 - grid.x0) * (grid.y1 - grid.y0) * (grid.z1 - grid.z0)
}