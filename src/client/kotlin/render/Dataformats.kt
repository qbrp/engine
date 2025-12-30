package org.lain.engine.client.render

interface Vec2 {
    val x: Float
    val y: Float
    fun scale(factor: Float): Vec2 {
        return MutableVec2(x * factor, y * factor)
    }
    fun divide(factor: Float): Vec2 {
        return MutableVec2(x / factor, y / factor)
    }
    fun add(x: Float, y: Float): Vec2 {
        return MutableVec2(this.x + x, this.y + y)
    }
    fun sub(x: Float, y: Float): Vec2 {
        return add(-x, -y)
    }
    fun add(vec2: Vec2): Vec2 {
        return add(vec2.x, vec2.y)
    }
}

fun Vec2(x: Float, y: Float): Vec2 = MutableVec2(x, y)

data class MutableVec2(override var x: Float, override var y: Float): Vec2 {
    constructor(vec2: Vec2) : this(vec2.x, vec2.y)
}