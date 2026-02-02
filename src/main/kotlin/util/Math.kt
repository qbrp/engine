package org.lain.engine.util

import org.lain.engine.player.EnginePlayer
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.players
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

const val DEGREES_TO_RADIANS_DOUBLE = 0.017453292519943295

const val DEGREES_TO_RADIANS_FLOAT = 0.017453292f

fun toRadians(angdeg: Double): Double {
    return angdeg * DEGREES_TO_RADIANS_DOUBLE
}

fun toRadians(angdeg: Float): Float {
    return angdeg * DEGREES_TO_RADIANS_FLOAT
}

fun isPowerOfTwo(n: Int): Boolean {
    return (n > 0) && ((n and (n - 1)) == 0)
}

fun smootherstep(t: Float): Float {
    return 6*t*t*t*t*t - 15*t*t*t*t + 10*t*t*t
}

fun smoothstep(t: Float): Float {
    return 3*t*t - 2*t*t*t
}

fun lerp(start: Float, end: Float, t: Float): Float {
    return start * (1 - t) + end * t
}

fun easeInStep(current: Float, target: Float, deltaTick: Float, smoothing: Float = 0.8f): Float {
    val t = 1f - smoothing.pow(deltaTick)
    return current + (target - current) * t
}

fun clampDelta(cur: Float, end: Float, threshold: Float): Float {
    return if (abs(end - cur) < threshold) {
        end
    } else {
        cur
    }
}

fun roundToInt(float: Float): Int = round(float).toInt()

fun roundToInt(double: Double): Int = round(double).toInt()

fun filterNearestPlayers(
    world: World,
    pos: Pos,
    radius: Int,
    players: List<EnginePlayer> = world.players
): List<EnginePlayer> {
    return players.filter {
        val l = it.get<Location>() ?: return@filter false
        l.world == world && l.position.squaredDistanceTo(pos) <= radius*radius
    }
}

fun filterNearestPlayers(
    location: Location,
    radius: Int,
    players: List<EnginePlayer> = location.world.players
): List<EnginePlayer> {
    return filterNearestPlayers(location.world, location.position, radius, players)
}

fun EnginePlayer.filterNearestPlayers(radius: Int): List<EnginePlayer> {
    val location = get<Location>() ?: return emptyList()
    return filterNearestPlayers(location, radius)
}

inline fun <T> Iterable<T>.sumOf(selector: (T) -> Float): Float {
    var sum: Float = 0f
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

fun parseHexColor(string: String): Int   {
    return string.removePrefix("#").toLong(16).toInt()
}