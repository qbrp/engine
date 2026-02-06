package org.lain.engine.util

interface Vec2 {
    val x: Float
    val y: Float
    fun scale(factor: Float): Vec2 {
        return MutableVec2(x * factor, y * factor)
    }
    fun scale(vec2: Vec2): Vec2 {
        return MutableVec2(this.x * vec2.x, this.y * vec2.y)
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

fun Vec2(x: Float, y: Float = x): Vec2 = MutableVec2(x, y)

fun ZeroMutableVec2() = MutableVec2(0f, 0f)

fun ZeroVec2(): Vec2 = ZeroMutableVec2()

data class MutableVec2(override var x: Float, override var y: Float): Vec2 {
    constructor(vec2: Vec2) : this(vec2.x, vec2.y)

    override fun toString(): String {
        return "($x, $y)"
    }

    fun addMutate(x: Float, y: Float) {
        this.x += x
        this.y += y
    }

    fun addMutate(vec: Vec2) {
        addMutate(vec.x, vec.y)
    }

    fun set(x: Float = this.x, y: Float = this.y) {
        this.x = x
        this.y = y
    }

    fun set(vec2: Vec2) {
        set(vec2.x, vec2.y)
    }
}