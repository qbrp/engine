package org.lain.engine.chat.acoustic

import org.lain.engine.util.PrimitiveArrayPool

data class SceneSize(val width: Int, val height: Int, val depth: Int) {
    val length = width * height * depth
}

data class Grid3Range(
    val x0: Int, val x1: Int,
    val y0: Int, val y1: Int,
    val z0: Int, val z1: Int
)

interface Grid3<T> {
    val w: Int
    val h: Int
    val d: Int
    val size: Int
        get() = w * h * d
    val arraySize: Int
        get() = size - 1

    fun posOf(i: Int): Triple<Int, Int, Int> {
        return posOf(i, w, h, d)
    }

    fun posOf(i: Int, out: MutableVec3i) {
        posOf(i, w, h, d, out)
    }

    fun indexOf(x: Int, y: Int, z: Int) = z * (w * h) + y * (w) + x

    fun inBounds(x: Int, y: Int, z: Int) = x in 0..w && y in 0..h && z in 0..d

    operator fun get(idx: Int): T

    operator fun get(x: Int, y: Int, z: Int): T

    fun forEach(
        chunk: Grid3Range,
        block: (idx: Int, x: Int, y: Int, z: Int) -> Unit
    ) {
        forEach(
            chunk.x0, chunk.x1,
            chunk.y0, chunk.y1,
            chunk.z0, chunk.z1,
            block
        )
    }

    fun forEachLinear(
        start: Int, end: Int,
        block: (idx: Int, x: Int, y: Int, z: Int) -> Unit
    )

    fun forEach(
        x0: Int = 0, x1: Int = w,
        y0: Int = 0, y1: Int = h,
        z0: Int = 0, z1: Int = d,
        block: (idx: Int, x: Int, y: Int, z: Int) -> Unit
    ) {
        for (z in z0 until z1) {
            val zOff = z * w * h
            for (y in y0 until y1) {
                val yOff = y * w
                for (x in x0 until x1) {
                    val idx = zOff + yOff + x
                    block(idx, x, y, z)
                }
            }
        }
    }

    companion object {
        fun posOf(i: Int, w: Int, h: Int, d: Int): Triple<Int, Int, Int> {
            val z = i / (w * h)
            val rem = i % (w * h)
            val y = rem / w
            val x = rem % w

            return Triple(x, y, z)
        }

        fun posOf(i: Int, w: Int, h: Int, d: Int, out: MutableVec3i) {
            val z = i / (w * h)
            val rem = i % (w * h)
            val y = rem / w
            val x = rem % w
            out.x = x
            out.y = y
            out.z = z
        }
    }
}

data class MutableVec3i(var x: Int, var y: Int, var z: Int)

interface MutableGrid3<T> : Grid3<T> {
    operator fun set(x: Int, y: Int, z: Int, value: T)

    operator fun set(idx: Int, value: T)

    fun fill(elem: T)

    fun map(
        chunk: Grid3Range = Grid3Range(0, w, 0, h, 0,d),
        block: (idx: Int, x: Int, y: Int, z: Int) -> T
    ) {
        forEach(chunk) { idx, x, y, z ->
            this[idx] = block(idx, x, y, z)
        }
    }
}

abstract class AbstractGrid3<T>(
    override val w: Int,
    override val h: Int,
    override val d: Int
) : MutableGrid3<T> {
    override val size: Int = w * h * d

    override operator fun get(x: Int, y: Int, z: Int): T = this[indexOf(x, y, z)]

    override operator fun set(x: Int, y: Int, z: Int, value: T) {
        this[indexOf(x, y, z)] = value
    }
}

open class GenericGrid3<T>(
    w: Int,
    h: Int,
    d: Int,
    protected val array: Array<T>
) : AbstractGrid3<T>(w, h, d) {
    override val size: Int
        get() = array.size

    override operator fun get(idx: Int): T = array[idx]

    override fun forEachLinear(start: Int, end: Int, block: (Int, Int, Int, Int) -> Unit) {
        array.forEachIndexed { i, elem ->
            val (x, y, z) = posOf(i)
            if (i == end) return@forEachIndexed
            block(i, x, y, z)
        }
    }

    override fun set(idx: Int, value: T) {
        array[idx] = value
    }

    override fun fill(elem: T) {
        array.fill(elem)
    }
}

class Grid3f internal constructor(
    w: Int,
    h: Int,
    d: Int,
    internal val array: FloatArray
) : AbstractGrid3<Float>(w, h, d)
{
    constructor(
        w: Int,
        h: Int,
        d: Int,
        factory: (Int) -> Float = { 0f }
    ): this(w, h, d, FloatArray(w * h * d, factory))

    override fun get(idx: Int): Float = array[idx]

    override fun set(idx: Int, value: Float) {
        array[idx] = value
    }

    fun arraycopy(other: Grid3f) {
        require(this.array.size == other.array.size) { "Grid sizes do not match" }
        System.arraycopy(this.array, 0, other.array, 0, this.array.size)
    }

    override fun fill(elem: Float) {
        array.fill(elem)
    }

    fun clear() {
        fill(0f)
    }

    override fun forEachLinear(start: Int, end: Int, block: (Int, Int, Int, Int) -> Unit) {
        array.forEachIndexed { i, elem ->
            val (x, y, z) = posOf(i)
            if (i == end) return@forEachIndexed
            block(i, x, y, z)
        }
    }
}

fun PrimitiveArrayPool.getGrid3f(size: SceneSize) = getGrid3f(size.width, size.height, size.depth)

fun PrimitiveArrayPool.getGrid3f(w: Int, h: Int, d: Int) = Grid3f(w, h, d, getFloat(w * h * d))

fun PrimitiveArrayPool.freeGrid3f(grid: Grid3f) = free(grid.array)

inline fun <reified T> Grid3(
    w: Int,
    h: Int,
    d: Int,
    factory: (Int, Int, Int, Int) -> T
) = GenericGrid3(w, h, d, Array<T>(w * h * d) {
    val (x, y, z) = Grid3.posOf(it, w, h, d)
    factory(it, x, y, z)
})