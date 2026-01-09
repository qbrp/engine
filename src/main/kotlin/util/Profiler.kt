package org.lain.engine.util

data class ServerStats(val averageTickTimeMillis: Int)

fun getServerStats(tickTimes: List<Int>): ServerStats {
    return ServerStats(tickTimes.average().toInt())
}