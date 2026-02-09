package org.lain.engine.chat.acoustic

import org.lain.engine.mc.ChunkedAcousticView
import org.lain.engine.player.EnginePlayer
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.math.Pos
import org.lain.engine.world.WorldId
import java.util.PriorityQueue

interface AcousticSimulator {
    suspend fun simulateSingleSource(
        world: WorldId,
        pos: Pos,
        volume: Float,
        maxVolume: Float,
        attenuation: Float,
    ): AcousticSimulationResult
}

interface AcousticSimulationResult {
    fun debug(player: EnginePlayer, handler: ServerHandler, radius: Float)
    fun getVolume(pos: Pos): Float?
    fun finish()
}

class AcousticGeneration(
    val volume: Grid3f
)

data class Node(
    val x: Int,
    val y: Int,
    val z: Int,
    val volume: Float
)
fun simulateAsyncDijkstra(
    view: ChunkedAcousticView,
    generation: AcousticGeneration,
    maxVolume: Float,
    attenuation: Float = 1f,
) {
    val volumeGrid = generation.volume
    val queue = PriorityQueue<Node> { a, b -> b.volume.compareTo(a.volume) }

    for (cx in 0 until volumeGrid.w) {
        for (cy in 0 until volumeGrid.h) {
            for (cz in 0 until volumeGrid.d) {
                val v = volumeGrid[cx, cy, cz]
                if (v > 0.0f) {
                    queue.add(Node(cx, cy, cz, v))
                }
            }
        }
    }

    val EPS = 1e-6f

    while (queue.isNotEmpty()) {
        val node = queue.poll()
        val x = node.x
        val y = node.y
        val z = node.z
        val v = node.volume

        if (v + EPS < volumeGrid[x, y, z]) continue

        for (offset in NEIGHBOURS_VON_NEUMANN) {
            val nx = x + offset[0]
            val ny = y + offset[1]
            val nz = z + offset[2]

            if (nx !in 0 until volumeGrid.w || ny !in 0 until volumeGrid.h || nz !in 0 until volumeGrid.d)
                continue

            val pass = view.getPassability(nx, ny, nz)
            if (pass <= 0f) continue

            var spread = v * pass * attenuation
            if (spread <= 0.01f) continue

            if (spread > maxVolume) spread = maxVolume

            val current = volumeGrid[nx, ny, nz]
            if (spread > current + EPS) {
                volumeGrid[nx, ny, nz] = spread
                queue.add(Node(nx, ny, nz, spread))
            }
        }
    }
}
