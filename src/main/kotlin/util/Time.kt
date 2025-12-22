package org.lain.engine.util

@JvmInline
value class Timestamp(val timeMillis: Long) {
    fun timeElapsed() = System.currentTimeMillis() - timeMillis
}

fun Timestamp() = Timestamp(System.currentTimeMillis())