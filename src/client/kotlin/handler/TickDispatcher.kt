package org.lain.engine.client.handler

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class TickDispatcher {
    private val waiters = mutableListOf<Continuation<Unit>>()

    suspend fun waitNextTick() {
        suspendCancellableCoroutine { continuation ->
            waiters += continuation
        }
    }

    fun tick() {
        val current = waiters.toList()
        waiters.clear()

        current.forEach {
            it.resume(Unit)
        }
    }
}