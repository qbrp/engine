package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lain.engine.chat.acoustic.AcousticGeneration
import org.lain.engine.chat.acoustic.AcousticNodeHeap
import org.lain.engine.chat.acoustic.AcousticResultLifetime
import org.lain.engine.chat.acoustic.AcousticSceneView
import org.lain.engine.chat.acoustic.Grid3f
import org.lain.engine.chat.acoustic.simulateDijkstra
import java.util.PriorityQueue
import kotlin.random.Random

class AcousticSimulationTest {
    @Test
    fun propagatesThroughOpenVoxelsAndStopsAtSolidVoxels() {
        val passability = Grid3f(5, 1, 1) { 1f }
        passability[3, 0, 0] = 0f
        val generation = AcousticGeneration(Grid3f(5, 1, 1))
        generation.seed(0, 0, 0, 1f)

        simulateDijkstra(passability.view(), generation, maxVolume = 1f, attenuation = 0.5f)

        assertEquals(1f, generation.volume[0, 0, 0], FLOAT_TOLERANCE)
        assertEquals(0.5f, generation.volume[1, 0, 0], FLOAT_TOLERANCE)
        assertEquals(0.25f, generation.volume[2, 0, 0], FLOAT_TOLERANCE)
        assertEquals(0f, generation.volume[3, 0, 0], FLOAT_TOLERANCE)
        assertEquals(0f, generation.volume[4, 0, 0], FLOAT_TOLERANCE)
    }

    @Test
    fun optimizedSimulationMatchesReferenceDijkstra() {
        val random = Random(0xAC0571C)

        repeat(30) {
            val passability = Grid3f(6, 5, 4) {
                PASSABILITY_VALUES[random.nextInt(PASSABILITY_VALUES.size)]
            }
            val seeds = buildList {
                repeat(4) {
                    add(
                        Seed(
                            random.nextInt(passability.w),
                            random.nextInt(passability.h),
                            random.nextInt(passability.d),
                            random.nextFloat() * 1.5f + 0.1f,
                        )
                    )
                }
            }
            val maxVolume = 1.25f
            val attenuation = 0.91f
            val expected = referenceSimulation(passability, seeds, maxVolume, attenuation)
            val actual = AcousticGeneration(Grid3f(passability.w, passability.h, passability.d))
            seeds.forEach { seed -> actual.seed(seed.x, seed.y, seed.z, seed.volume) }

            simulateDijkstra(passability.view(), actual, maxVolume, attenuation)

            repeat(expected.size) { index ->
                assertEquals(expected[index], actual.volume[index], FLOAT_TOLERANCE, "volume[$index]")
            }
        }
    }

    @Test
    fun primitiveHeapKeepsDescendingOrderWhileGrowing() {
        val heap = AcousticNodeHeap(initialCapacity = 2)
        val entries = List(200) { index -> index to ((index * 37) % 101).toFloat() }

        try {
            entries.forEach { (index, volume) -> heap.add(index, volume) }

            var previous = Float.POSITIVE_INFINITY
            while (heap.isNotEmpty()) {
                assertTrue(heap.maxVolume <= previous)
                previous = heap.maxVolume
                heap.removeMax()
            }
        } finally {
            heap.close()
        }
    }

    @Test
    fun resultLifetimeDefersAndDeduplicatesResourceRelease() {
        var releases = 0
        val lifetime = AcousticResultLifetime { releases += 1 }
        var immediateReleases = 0
        val immediateLifetime = AcousticResultLifetime { immediateReleases += 1 }

        immediateLifetime.finish()
        immediateLifetime.finish()
        assertEquals(1, immediateReleases)

        assertTrue(lifetime.retain())
        lifetime.finish()
        lifetime.finish()
        assertEquals(0, releases)

        lifetime.release()
        assertEquals(1, releases)

        lifetime.finish()
        assertEquals(1, releases)
        assertFalse(lifetime.retain())
    }

    private fun referenceSimulation(
        passability: Grid3f,
        seeds: List<Seed>,
        maxVolume: Float,
        attenuation: Float,
    ): Grid3f {
        val result = Grid3f(passability.w, passability.h, passability.d)
        val queue = PriorityQueue<ReferenceNode> { left, right -> right.volume.compareTo(left.volume) }

        seeds.forEach { seed ->
            if (seed.volume > result[seed.x, seed.y, seed.z]) {
                result[seed.x, seed.y, seed.z] = seed.volume
            }
        }
        seeds.map { seed -> ReferenceNode(seed.x, seed.y, seed.z, result[seed.x, seed.y, seed.z]) }
            .distinctBy { node -> result.indexOf(node.x, node.y, node.z) }
            .forEach(queue::add)

        while (queue.isNotEmpty()) {
            val node = queue.remove()
            if (node.volume + FLOAT_TOLERANCE < result[node.x, node.y, node.z]) continue

            for (offset in REFERENCE_OFFSETS) {
                val x = node.x + offset[0]
                val y = node.y + offset[1]
                val z = node.z + offset[2]
                if (!result.inBounds(x, y, z)) continue

                val pass = passability[x, y, z]
                if (pass <= 0f) continue

                val spread = minOf(node.volume * pass * attenuation, maxVolume)
                if (spread <= MINIMUM_VOLUME || spread <= result[x, y, z] + FLOAT_TOLERANCE) continue

                result[x, y, z] = spread
                queue += ReferenceNode(x, y, z, spread)
            }
        }

        return result
    }

    private fun Grid3f.view() = AcousticSceneView { x, y, z -> this[x, y, z] }

    private data class Seed(val x: Int, val y: Int, val z: Int, val volume: Float)
    private data class ReferenceNode(val x: Int, val y: Int, val z: Int, val volume: Float)

    private companion object {
        const val FLOAT_TOLERANCE = 1e-6f
        const val MINIMUM_VOLUME = 0.01f

        val PASSABILITY_VALUES = floatArrayOf(0f, 0.2f, 0.55f, 0.8f, 0.95f, 1f)
        val REFERENCE_OFFSETS = arrayOf(
            intArrayOf(1, 0, 0),
            intArrayOf(-1, 0, 0),
            intArrayOf(0, 1, 0),
            intArrayOf(0, -1, 0),
            intArrayOf(0, 0, 1),
            intArrayOf(0, 0, -1),
        )
    }
}
