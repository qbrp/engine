package org.lain.engine.client.render

interface Vec2 {
    val x: Float
    val y: Float
    fun scale(factor: Float): Vec2
    fun divide(factor: Float): Vec2
    fun add(x: Float, y: Float): Vec2
    fun add(vec2: Vec2): Vec2 {
        return add(vec2.x, vec2.y)
    }
}

@JvmInline
value class ZIndex(val value: Float)

data class ImmutableVec2(override val x: Float, override val y: Float): Vec2 {
    override fun scale(factor: Float) = ImmutableVec2(x * factor, y * factor)
    override fun divide(factor: Float) = ImmutableVec2(x / factor, y / factor)
    override fun add(x: Float, y: Float): Vec2 = ImmutableVec2(this.x + x, this.y + y)
}

data class MutableVec2(override var x: Float, override var y: Float): Vec2 {
    override fun scale(factor: Float): Vec2 {
        x *= factor
        y * factor
        return this
    }

    override fun divide(factor: Float): Vec2 {
        x /= factor
        y /= factor
        return this
    }

    override fun add(x: Float, y: Float): Vec2 {
        this.x + x
        this.y + y
        return this
    }

    companion object {
        fun of(vec2: Vec2) = MutableVec2(vec2.x, vec2.y)
    }
}