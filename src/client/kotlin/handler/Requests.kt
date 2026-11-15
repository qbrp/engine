package org.lain.engine.client.handler

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.lain.engine.client.transport.ClientTransport
import org.lain.engine.client.util.MinecraftClientDispatcher
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import kotlin.time.Duration.Companion.seconds

class ClientRequestHandler(
    private val transport: ClientTransport,
    private val dispatcher: CoroutineDispatcher = MinecraftClientDispatcher,
) {
    private class PendingRequest(
        val endpointId: String,
        val response: CompletableDeferred<Packet> = CompletableDeferred(),
    )

    private val lock = Any()
    private var pending: PendingRequest? = null
    private var closed = false

    fun <P : Packet> register(endpoint: Endpoint<P>) {
        transport.registerEndpoint(endpoint) {
            onResponse(endpoint, this)
        }
    }

    fun <P : Packet> onResponse(endpoint: Endpoint<P>, packet: P) {
        val request = synchronized(lock) {
            pending?.takeIf { it.endpointId == endpoint.identifier }
        }
        request?.response?.complete(packet)
    }

    fun close() {
        val request = synchronized(lock) {
            closed = true
            pending.also { pending = null }
        }
        request?.response?.cancel()
    }

    suspend fun <P : Packet> await(endpoint: Endpoint<P>, action: suspend () -> Unit = {}): P =
        withTimeoutOrNull(5.seconds) {
            val request = PendingRequest(endpoint.identifier)
            synchronized(lock) {
                check(!closed) { "Обработчик запросов входа закрыт" }
                check(pending == null) { "Запрос уже выполняется" }
                pending = request
            }
            try {
                withContext(dispatcher) { action() }
                endpoint.kclass.java.cast(request.response.await())
            } finally {
                synchronized(lock) {
                    if (pending === request) pending = null
                }
                request.response.cancel()
            }
        }
            ?: error("Превышено время ожидания")
}
