package org.lain.engine.chat.acoustic

import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.lain.engine.mc.ChunkedAcousticView
import org.lain.engine.util.Pos
import org.lain.engine.util.PrimitiveArrayPool
import org.lain.engine.util.Timestamp
import org.lain.engine.util.flush
import org.lain.engine.world.WorldId
import org.slf4j.Logger
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder
import kotlin.collections.plusAssign
import kotlin.math.max
import kotlin.math.min

interface AcousticSimulator {
    suspend fun simulateSingleSource(world: WorldId, pos: Pos, volume: Float, maxVolume: Float): AcousticSimulationResult
}

interface AcousticSimulationResult {
    fun getVolume(pos: Pos): Float?
    fun finish()
}

class AcousticGeneration(
    val volume: Grid3f
)

fun simulateAsync(
    view: ChunkedAcousticView,
    generation: AcousticGeneration,
    executors: ExecutorService,
    threads: Int,
    steps: Int,
    logger: Logger,
    performanceDebug: Boolean,
    maxVolume: Float,
    chunkSize: Int = 16,
) {
    fun debug(str: String) {
        if (performanceDebug) logger.info("[DEBUG] $str")
    }

    val prepareStart = Timestamp()
    val computing = LongAdder()
    val transforming = LongAdder()

    val size = view.totalSize

    val volumeGrid = generation.volume
    val deltaGrid = PrimitiveArrayPool.getGrid3f(size)

    // Начальный прогон по чанкам, чтобы пометить активный

    val chunksList = splitIntoChunks(size, chunkSize)
    val chunkMap = chunkMapOf(size, chunksList, chunkSize)
    val chunks = chunkMap.size
    val nextActive = Collections.synchronizedList(MutableList(chunkMap.size) { false })
    chunkMap.forEach { idx, x, y, z ->
        val chunk = chunkMap[idx]
        volumeGrid.forEach(chunk.grid) { idx, x, y, z ->
            if (volumeGrid[idx] != 0f) {
                chunk.isActive = true
            }
        }
    }

    val threadsToUse = threads.coerceIn(0, chunks)
    val globalIndex = AtomicInteger(0)
    val chunkCoords = Array(chunks) { i -> MutableVec3i(0, 0, 0) }

    debug("Время подготовки симуляции: ${prepareStart.timeElapsed()} мл.")

    for (i in 0..steps) {
        fun markActive(x: Int, y: Int, z: Int) {
            val index = chunkMap.indexOf(x, y, z)
            if (index !in 0 until chunkMap.size) return
            nextActive[index] = true
        }

        val latch = CountDownLatch(threadsToUse)

        for (t in 0 until threadsToUse) {
            executors.submit {
                while (true) {
                    try {
                        val i = globalIndex.getAndIncrement()
                        if (i >= chunks) break

                        val chunk = chunkMap[i]
                        val chunkPos = chunkCoords[i]
                        val (chunkX, chunkY, chunkZ) = chunkPos
                        chunkMap.posOf(i, chunkPos)

                        if (!chunk.isActive || chunk.isRaiseFinished) continue

                        val grid = chunk.grid

                        view.forEachCell(grid) { _, passability, x, y, z ->
                            if (volumeGrid[x, y, z] != 0f) return@forEachCell

                            val volume = collectVolume(volumeGrid, x, y, z)
                            val delta = volume * passability

                            deltaGrid[x, y, z] = min(delta, maxVolume)

                            if (delta < 0.01f) return@forEachCell
                            chunk.raised += 1

                            if (x == grid.x0) markActive(chunkX - 1, chunkY, chunkZ)
                            else if (x == grid.x1 - 1) markActive(chunkX + 1, chunkY, chunkZ)

                            if (y == grid.y0) markActive(chunkX, chunkY - 1, chunkZ)
                            else if (y == grid.y1 - 1) markActive(chunkX, chunkY + 1, chunkZ)

                            if (z == grid.z0) markActive(chunkX, chunkY, chunkZ - 1)
                            else if (z == grid.z1 - 1) markActive(chunkX, chunkY, chunkZ + 1)
                        }
                    } catch (e: Exception) {
                        logger.error("Возникла ошибка при симуляции акустики", e)
                        break
                    }
                }
                latch.countDown()
            }
        }

        val computingStart = Timestamp()

        latch.await()

        computing.add(computingStart.timeElapsed())

        val transformingStart = Timestamp()

        for (i in nextActive.indices) {
            val isActive = nextActive[i]
            if (isActive) {
                chunkMap[i].isActive = true
            }
        }

        val edited = transformVolume(
            volumeGrid,
            deltaGrid,
        )

        globalIndex.set(0)
        deltaGrid.clear()
        nextActive.fill(false)

        transforming.add(transformingStart.timeElapsed())

        if (edited == 0) {
            break
        }
    }

    debug("Расчёт клеток: $computing мл.")
    debug("Применение: $transforming мл.")

    val workedChunks = chunksList.count { it.isActive }
    debug("Заполненность: ${(workedChunks / chunks.toFloat() * 100).toInt()}%")

    PrimitiveArrayPool.freeGrid3f(deltaGrid)
}