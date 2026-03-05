package org.lain.engine.util

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class Timestamp(val timeMillis: Long) {
    fun timeElapsed() = System.currentTimeMillis() - timeMillis
}

fun Timestamp() = Timestamp(System.currentTimeMillis())