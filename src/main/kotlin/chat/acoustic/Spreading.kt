package org.lain.engine.chat.acoustic

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

fun transformVolume(base: Grid3f, delta: Grid3f): Int {
    var edited = 0
    for (idx in 0 until base.size) {
        val delta = delta[idx]
        if (delta != 0f) {
            base[idx] = base[idx] + delta
            edited += 1
        }
    }
    return edited
}

fun transformVolumeParallel(
    base: Grid3f,
    delta: Grid3f,
    pool: ExecutorService
) {
    val size = base.size
    val threads = Runtime.getRuntime().availableProcessors()
    val chunk = size / threads

    val jobs = ArrayList<Future<*>>(threads)

    for (t in 0 until threads) {
        val start = t * chunk
        val end = if (t == threads - 1) size else start + chunk

        jobs += pool.submit {
            for (i in start until end) {
                val old = base[i]
                val d = delta[i]
                if (old == 0f && d != 0f) {
                    base[i] = (old + d).coerceIn(0f, 1f)
                }
            }
        }
    }

    jobs.forEach { it.get() }
}

val NEIGHBOURS_VON_NEUMANN = arrayOf(
    intArrayOf(1, 0, 0),
    intArrayOf(-1, 0, 0),
    intArrayOf(0, 1, 0),
    intArrayOf(0, -1, 0),
    intArrayOf(0, 0, 1),
    intArrayOf(0, 0, -1)
)

fun collectVolume(vol: Grid3f, x: Int, y: Int, z: Int): Float {
    var weightedSum = 0f
    var weightTotal = 0f

    for ((i, offset) in NEIGHBOURS_VON_NEUMANN.withIndex()) {
        val nx = x + offset[0]
        val ny = y + offset[1]
        val nz = z + offset[2]

        if (nx in 0 until vol.w && ny in 0 until vol.h && nz in 0 until vol.d) {
            val v = vol[nx, ny, nz]
            if (v > 0f) {
                val w = v
                weightedSum += v * w
                weightTotal += w
            }
        }
    }

    return if (weightTotal > 0f) weightedSum / weightTotal else 0f
}