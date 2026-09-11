package org.lain.engine.chat.acoustic

import org.lain.engine.util.PrimitiveArrayPool

class AcousticGeneration(
    val volume: Grid3f
) {
    private var sourceIndices = IntArray(INITIAL_SOURCE_CAPACITY)
    private var sourceCount = 0

    fun seed(x: Int, y: Int, z: Int, value: Float) {
        require(volume.inBounds(x, y, z)) { "Acoustic source is outside of the volume grid" }

        val index = volume.indexOf(x, y, z)
        val oldValue = volume.array[index]
        if (value <= oldValue) return

        volume.array[index] = value
        if (oldValue <= 0f && value > 0f) {
            ensureSourceCapacity(sourceCount + 1)
            sourceIndices[sourceCount++] = index
        }
    }

    internal fun enqueueSources(queue: AcousticNodeHeap) {
        repeat(sourceCount) { sourceIndex ->
            val index = sourceIndices[sourceIndex]
            val value = volume.array[index]
            if (value > 0f) {
                queue.add(index, value)
            }
        }
    }

    private fun ensureSourceCapacity(requiredCapacity: Int) {
        if (requiredCapacity <= sourceIndices.size) return
        sourceIndices = sourceIndices.copyOf(maxOf(requiredCapacity, sourceIndices.size * 2))
    }

    private companion object {
        const val INITIAL_SOURCE_CAPACITY = 8
    }
}

fun simulateDijkstra(
    view: AcousticSceneView,
    generation: AcousticGeneration,
    maxVolume: Float,
    attenuation: Float = 1f,
) {
    val volumeGrid = generation.volume
    val volumes = volumeGrid.array
    val width = volumeGrid.w
    val height = volumeGrid.h
    val depth = volumeGrid.d
    val layerSize = width * height
    val queue = AcousticNodeHeap()

    generation.enqueueSources(queue)

    queue.use { queue ->
        while (queue.isNotEmpty()) {
            val index = queue.maxIndex
            val sourceVolume = queue.maxVolume
            queue.removeMax()

            if (sourceVolume + VOLUME_EPSILON < volumes[index]) continue

            val z = index / layerSize
            val layerIndex = index - z * layerSize
            val y = layerIndex / width
            val x = layerIndex - y * width

            if (x + 1 < width) {
                spreadTo(
                    index + 1, x + 1, y, z,
                    sourceVolume, view, volumes, maxVolume, attenuation, queue,
                )
            }
            if (x > 0) {
                spreadTo(
                    index - 1, x - 1, y, z,
                    sourceVolume, view, volumes, maxVolume, attenuation, queue,
                )
            }
            if (y + 1 < height) {
                spreadTo(
                    index + width, x, y + 1, z,
                    sourceVolume, view, volumes, maxVolume, attenuation, queue,
                )
            }
            if (y > 0) {
                spreadTo(
                    index - width, x, y - 1, z,
                    sourceVolume, view, volumes, maxVolume, attenuation, queue,
                )
            }
            if (z + 1 < depth) {
                spreadTo(
                    index + layerSize, x, y, z + 1,
                    sourceVolume, view, volumes, maxVolume, attenuation, queue,
                )
            }
            if (z > 0) {
                spreadTo(
                    index - layerSize, x, y, z - 1,
                    sourceVolume, view, volumes, maxVolume, attenuation, queue,
                )
            }
        }
    }
}

private fun spreadTo(
    targetIndex: Int,
    x: Int,
    y: Int,
    z: Int,
    sourceVolume: Float,
    view: AcousticSceneView,
    volumes: FloatArray,
    maxVolume: Float,
    attenuation: Float,
    queue: AcousticNodeHeap,
) {
    val passability = view.getPassability(x, y, z)
    if (passability <= 0f) return

    val spread = minOf(sourceVolume * passability * attenuation, maxVolume)
    if (spread <= MINIMUM_PROPAGATED_VOLUME) return

    if (spread > volumes[targetIndex] + VOLUME_EPSILON) {
        volumes[targetIndex] = spread
        queue.add(targetIndex, spread)
    }
}

internal class AcousticResultLifetime(
    private val releaseResources: () -> Unit,
) {
    private val lock = Any()
    private var retainedUsers = 0
    private var finishRequested = false
    private var resourcesReleased = false

    fun retain(): Boolean = synchronized(lock) {
        if (finishRequested || resourcesReleased) {
            false
        } else {
            retainedUsers += 1
            true
        }
    }

    fun release() {
        val shouldRelease = synchronized(lock) {
            check(retainedUsers > 0) { "Acoustic result released without a matching retain" }
            retainedUsers -= 1
            markReleasedIfReady()
        }
        if (shouldRelease) releaseResources()
    }

    fun finish() {
        val shouldRelease = synchronized(lock) {
            finishRequested = true
            markReleasedIfReady()
        }
        if (shouldRelease) releaseResources()
    }

    private fun markReleasedIfReady(): Boolean {
        if (!finishRequested || retainedUsers != 0 || resourcesReleased) return false
        resourcesReleased = true
        return true
    }
}

internal class AcousticNodeHeap(initialCapacity: Int = INITIAL_HEAP_CAPACITY) : AutoCloseable {
    private var indices = PrimitiveArrayPool.getInt(maxOf(1, initialCapacity))
    private var volumes = PrimitiveArrayPool.getFloat(indices.size)
    private var closed = false

    var size: Int = 0
        private set

    val maxIndex: Int
        get() {
            check(size > 0) { "Acoustic heap is empty" }
            return indices[0]
        }

    val maxVolume: Float
        get() {
            check(size > 0) { "Acoustic heap is empty" }
            return volumes[0]
        }

    fun isNotEmpty(): Boolean = size != 0

    fun add(index: Int, volume: Float) {
        ensureCapacity(size + 1)

        var child = size
        size += 1
        while (child > 0) {
            val parent = (child - 1) ushr 1
            if (volumes[parent] >= volume) break
            indices[child] = indices[parent]
            volumes[child] = volumes[parent]
            child = parent
        }
        indices[child] = index
        volumes[child] = volume
    }

    fun removeMax() {
        check(size > 0) { "Acoustic heap is empty" }

        size -= 1
        if (size == 0) return

        val lastIndex = indices[size]
        val lastVolume = volumes[size]
        var parent = 0

        while (true) {
            val left = parent * 2 + 1
            if (left >= size) break

            val right = left + 1
            val largestChild = if (right < size && volumes[right] > volumes[left]) right else left
            if (volumes[largestChild] <= lastVolume) break

            indices[parent] = indices[largestChild]
            volumes[parent] = volumes[largestChild]
            parent = largestChild
        }

        indices[parent] = lastIndex
        volumes[parent] = lastVolume
    }

    override fun close() {
        if (closed) return
        closed = true
        PrimitiveArrayPool.free(indices)
        PrimitiveArrayPool.free(volumes)
        size = 0
    }

    private fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity <= indices.size) return

        val newCapacity = maxOf(requiredCapacity, indices.size * 2)
        val newIndices = PrimitiveArrayPool.getInt(newCapacity)
        val newVolumes = PrimitiveArrayPool.getFloat(newCapacity)
        System.arraycopy(indices, 0, newIndices, 0, size)
        System.arraycopy(volumes, 0, newVolumes, 0, size)
        PrimitiveArrayPool.free(indices)
        PrimitiveArrayPool.free(volumes)
        indices = newIndices
        volumes = newVolumes
    }

    private companion object {
        const val INITIAL_HEAP_CAPACITY = 64
    }
}

private const val VOLUME_EPSILON = 1e-6f
private const val MINIMUM_PROPAGATED_VOLUME = 0.01f
