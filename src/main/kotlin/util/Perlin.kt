package org.lain.engine.util

class PerlinNoise(seed: Long) {
    private val p: IntArray by lazy(LazyThreadSafetyMode.NONE) {
        val arr = (0 until 256).toMutableList()
        val rnd = java.util.Random(seed)
        arr.shuffle(rnd)
        IntArray(512) { arr[it and 255] }
    }

    private fun fade(t: Float): Float =
        t * t * t * (t * (t * 6 - 15) + 10)

    private fun grad(hash: Int, x: Float): Float =
        if ((hash and 1) == 0) x else -x

    fun noise(x: Float): Float {
        val xi = kotlin.math.floor(x).toInt() and 255
        val xf = x - kotlin.math.floor(x)

        val u = fade(xf)

        val a = grad(p[xi], xf)
        val b = grad(p[xi + 1], xf - 1f)

        return (lerp(a, b, u) + 1f) * 0.5f
    }

    private fun lerp(a: Float, b: Float, t: Float): Float =
        a + t * (b - a)
}
