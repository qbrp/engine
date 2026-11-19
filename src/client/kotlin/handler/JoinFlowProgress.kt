package org.lain.engine.client.handler

import java.util.concurrent.atomic.AtomicInteger

class JoinFlowProgress {
    val value = AtomicInteger(0)

    fun commit() = value.getAndIncrement()

    companion object {
        // 2 коммита и 5 observable async
        const val MAX = 2 + 5
    }
}