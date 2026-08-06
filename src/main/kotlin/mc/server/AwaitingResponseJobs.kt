package org.lain.engine.mc.server

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal class AwaitingResponseJobs<K : Any, R : Any>(
    private val scope: CoroutineScope,
) {
    private data class PendingResponse<R>(
        val response: CompletableDeferred<R>,
        val job: Job,
    )

    private val pendingResponses = ConcurrentHashMap<K, PendingResponse<R>>()

    fun start(
        key: K,
        block: suspend (Deferred<R>) -> Unit,
    ): Job? {
        val response = CompletableDeferred<R>()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            block(response)
        }
        val pendingResponse = PendingResponse(response, job)

        if (pendingResponses.putIfAbsent(key, pendingResponse) != null) {
            job.cancel()
            return null
        }

        job.invokeOnCompletion {
            pendingResponses.remove(key, pendingResponse)
        }
        job.start()
        return job
    }

    fun complete(key: K, response: R): Boolean {
        return pendingResponses[key]?.response?.complete(response) == true
    }

    fun isPending(key: K): Boolean = pendingResponses.containsKey(key)

    fun cancel(key: K) {
        pendingResponses.remove(key)?.let { pending ->
            pending.response.cancel()
            pending.job.cancel()
        }
    }

    fun cancelAll() {
        pendingResponses.keys.toList().forEach(::cancel)
    }
}
