package org.lain.engine.util.math

import kotlinx.serialization.Serializable
import org.joml.Math
import org.lain.engine.world.VoxelPos
import kotlin.math.min
import kotlin.math.sqrt

val VEC3_ZERO = Vec3(0f, 0f, 0f)

fun Pos.cloneAsVec3(x: Float = this.x, y: Float = this.y, z: Float = this.z) = Vec3(x, y, z)

fun Pos.clonePos(x: Float = this.x, y: Float = this.y, z: Float = this.z): Pos = cloneAsVec3(x, y, z)

interface Pos {
    val x: Float
    val y: Float
    val z: Float
}

fun Pos.asVec3() = Vec3(this.x, this.y, this.z)

fun squaredDistanceTo(
    x1: Int, y1: Int,
    x2: Int, y2: Int
): Int {
    val dx = x1 - x2
    val dy = y1 - y2
    return dx * dx + dy * dy
}

interface Vec3 : Pos {
    fun length(): Float {
        return sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    fun normalize(): Vec3 {
        val d = sqrt(this.x * this.x + this.y * this.y + this.z * this.z)
        return if (d < 1.0E-4) VEC3_ZERO else Vec3(this.x / d, this.y / d, this.z / d)
    }

    fun horizontal(): Vec3 {
        return Vec3(this.x, 0f, this.z)
    }

    fun negate(): Vec3 {
        return mul(-1f)
    }

    fun lerp(vec3: Vec3, alpha: Float = 1f): Vec3 {
        return Vec3(
            x + (vec3.x - x) * alpha,
            y + (vec3.y - y) * alpha,
            z + (vec3.z - z) * alpha
        )
    }

    fun mul(scaleX: Float, scaleY: Float, scaleZ: Float): Vec3 {
        return Vec3(
            x * scaleX,
            y * scaleY,
            z * scaleZ
        )
    }


    fun mul(pos: Vec3): Vec3 {
        return mul(pos.x, pos.y, pos.z)
    }

    fun mul(scale: Float): Vec3 {
        return Vec3(x * scale, y * scale, z * scale)
    }

    fun mul(scale: Int): Vec3 {
        return mul(scale.toFloat())
    }

    fun add(x: Float = 0f, y: Float = 0f, z: Float = 0f, t: Float = 1f): Vec3 {
        return Vec3(x + this.x * t, y + this.y * t, z + this.z * t)
    }

    fun add(other: Pos, t: Float = 1f): Vec3 {
        return add(other.x, other.y, other.z, t)
    }

    fun sub(x: Float, y: Float, z: Float): Vec3 {
        return Vec3(this.x - x, this.y - y, this.z - z)
    }

    fun sub(other: Pos): Vec3 {
        return sub(other.x, other.y, other.z)
    }

    fun clampMax(x: Float = this.x, y: Float = this.y, z: Float = this.z): Vec3 {
        return Vec3(
            min(x, this.x),
            min(y, this.y),
            min(z, this.z)
        )
    }

    fun clampMax(value: Float): Vec3 {
        return clampMax(value, value, value)
    }

    fun squaredDistanceTo(to: Pos): Float {
        val dx = x - to.x
        val dy = y - to.y
        val dz = z - to.z
        return dx * dx + dy * dy + dz * dz
    }

    fun squaredDistanceTo(to: VoxelPos): Float {
        val dx = x - to.x
        val dy = y - to.y
        val dz = z - to.z
        return dx * dx + dy * dy + dz * dz
    }

    fun distance(vec: Pos): Float {
        val d = vec.x - this.x;
        val e = vec.y - this.y;
        val f = vec.z - this.z;
        return sqrt(d * d + e * e + f * f);
    }

    fun div(v: Vec3): Vec3 {
        return div(v, MutableVec3(this))
    }

    fun div(v: Vec3, dest: MutableVec3): Vec3 {
        dest.x = x / v.x
        dest.y = y / v.y
        dest.z = z / v.z
        return dest
    }

    fun div(scalar: Float): Vec3 {
        return Vec3(this.x / scalar, this.y / scalar, this.z / scalar)
    }

    fun cross(v: Vec3): Vec3 {
        val rx = Math.fma(y, v.z, -z * v.y)
        val ry = Math.fma(z, v.x, -x * v.z)
        val rz = Math.fma(x, v.y, -y * v.x)
        return Vec3(rx, ry, rz)
    }
}

fun Vec3(x: Float) = ImmutableVec3(x, x, x)

fun Vec3(x: Float, y: Float, z: Float) = ImmutableVec3(x, y, z)

fun Vec3(x: Int, y: Int, z: Int) = ImmutableVec3(x.toFloat(), y.toFloat(), z.toFloat())

@Serializable
data class ImmutableVec3(
    override val x: Float = 0f,
    override val y: Float = 0f,
    override val z: Float = 0f
) : Vec3 {
    override fun toString(): String {
        return "$x, $y, $z"
    }
    constructor(pos: Pos) : this(pos.x, pos.y, pos.z)
}

@Serializable
data class MutableVec3(
    override var x: Float = 0f,
    override var y: Float = 0f,
    override var z: Float = 0f
) : Vec3 {
    constructor(pos: Pos) : this(pos.x, pos.y, pos.z)

    override fun toString(): String {
        return "($x, $y, $z)"
    }

    fun set(vec: Vec3) {
        this.x = vec.x
        this.y = vec.y
        this.z = vec.z
    }

    fun set(x: Float, y: Float, z: Float) {
        this.x = x
        this.y = y
        this.z = z
    }

    fun mutateDiv(x: Float = this.x, y: Float = this.y, z: Float = this.z) {
        this.x /= x
        this.y /= y
        this.z /= z
    }

    fun mutateAdd(x: Float = this.x, y: Float = this.y, z: Float = this.z) {
        this.x = x
        this.y = y
        this.z = z
    }

    fun mutateLerp(x: Float, y: Float, z: Float, alpha: Float = 1f) {
        this.x += (x - this.x) * alpha
        this.y += (y - this.y) * alpha
        this.z += (z - this.z) * alpha
    }

    fun mutateLerp(vec3: Vec3, alpha: Float = 1f) {
        mutateLerp(vec3.x, vec3.y, vec3.z, alpha)
    }

    fun mutateSub(vec3: Vec3) {
        this.x -= vec3.x
        this.y -= vec3.y
        this.z -= vec3.z
    }

    fun addY(value: Float) {
        y += value
    }
}
