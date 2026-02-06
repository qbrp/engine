package org.lain.engine.chat.acoustic

import org.lain.engine.mc.ChunkedAcousticView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

fun transformVolume(base: Grid3f, new: Grid3f): Int {
    var edited = 0
    for (idx in 0 until base.size) {
        val new = new[idx]
        val old = base[idx]
        base[idx] = new
        if (new != old) {
            base[idx] = new
            edited += 1
        }
    }
    return edited
}

fun transformVolumeDeltas(base: Grid3f, delta: Grid3f, clamp: Float): Int {
    var edited = 0
    for (idx in 0 until base.size) {
        val old = base[idx]
        val new = (old + delta[idx]).coerceAtMost(clamp)
        if (new != old) {
            base[idx] = new
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

fun collectVolumeAvg(vol: Grid3f, x: Int, y: Int, z: Int): Float {
    var weightedSum = 0f
    var weightTotal = 0f

    for ((i, offset) in (NEIGHBOURS_VON_NEUMANN + intArrayOf(0, 0, 0)).withIndex()) {
        val nx = x + offset[0]
        val ny = y + offset[1]
        val nz = z + offset[2]

        if (nx in 0 until vol.w && ny in 0 until vol.h && nz in 0 until vol.d) {
            val v = vol[nx, ny, nz]
            if (v != 0f) {
                weightedSum += v
                weightTotal += 1
            }
        }
    }

    return if (weightTotal > 0f) weightedSum / weightTotal else 0f
}

fun collectVolume(vol: Grid3f, x: Int, y: Int, z: Int): Float {
    var sum = 0f

    for ((i, offset) in (NEIGHBOURS_VON_NEUMANN).withIndex()) {
        val nx = x + offset[0]
        val ny = y + offset[1]
        val nz = z + offset[2]

        if (nx in 0 until vol.w && ny in 0 until vol.h && nz in 0 until vol.d) {
            sum += vol[nx, ny, nz]
        }
    }

    return sum
}

fun spreadVolume(
    vol: Grid3f,
    view: ChunkedAcousticView,
    delta: Grid3f,
    forward: Grid3b,
    attenuation: Float,
    x: Int, y: Int, z: Int
): Boolean {
    val volume = vol[x, y, z]
    if (forward[x, y, z] || volume <= 0.01f) return false

    forward[x, y, z] = true

    for (offset in NEIGHBOURS_VON_NEUMANN) {
        val nx = x + offset[0]
        val ny = y + offset[1]
        val nz = z + offset[2]

        if (nx !in 0 until vol.w || ny !in 0 until vol.h || nz !in 0 until vol.d)
            continue

        val pass = view.getPassability(nx, ny, nz)
        if (pass <= 0f) continue

        val spread = volume * pass * attenuation
        val nvolume = vol[nx, ny, nz]

        if (spread > nvolume * 1.02f) {
            delta[nx, ny, nz] = spread
        }
    }

    return true
}
