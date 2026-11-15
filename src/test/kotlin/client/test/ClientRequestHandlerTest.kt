package org.lain.engine.client.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.lain.engine.client.handler.ClientRequestHandler
import org.lain.engine.client.transport.ClientPacketHandler
import org.lain.engine.client.transport.ClientTransport
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet

class ClientRequestHandlerTest {
    @Test
    fun immediateResponseIsNotLostBeforeAwaitStarts() = runBlocking {
        val transport = TestTransport()
        val requests = requests(transport)
        val response = Response("joined")
        transport.onSend = { requests.onResponse(responseEndpoint, response) }

        assertSame(response, requests.await(responseEndpoint) {
            transport.sendServerboundPacket(requestEndpoint, Request)
        })
        requests.close()
    }

    @Test
    fun closingPendingRequestsAndReceivingLatePacketsDoesNotCancelTheCaller() = runBlocking {
        val requests = requests()
        val pending = startWaiting(requests)

        requests.close()
        requests.close()
        requests.onResponse(responseEndpoint, Response("late"))
        pending.join()

        assertTrue(pending.isCancelled)
        assertTrue(currentCoroutineContext().isActive)
        assertThrows<IllegalStateException> { requests.await(responseEndpoint) }
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test
    fun cancellingARequestReleasesItsSlotForTheNextRequest() = runBlocking {
        val requests = requests()
        val cancelled = startWaiting(requests)
        cancelled.cancelAndJoin()
        val next = startWaiting(requests)
        val response = Response("retry")

        requests.onResponse(responseEndpoint, response)

        assertSame(response, next.await())
        assertTrue(currentCoroutineContext().isActive)
        requests.close()
    }

    @Test
    fun cancellingTheCallerRemovesItsPendingResponse() = runBlocking {
        val requests = requests()
        val caller = Job()
        val pending = CoroutineScope(caller + Dispatchers.Unconfined).startWaiting(requests)

        caller.cancel()
        pending.join()
        val next = startWaiting(requests)
        requests.onResponse(responseEndpoint, Response("retry"))

        assertEquals("retry", next.await().value)
        assertTrue(currentCoroutineContext().isActive)
        requests.close()
    }

    @Test
    fun failedSendPreservesTheOriginalErrorAndReleasesTheRequest() = runBlocking {
        val transport = TestTransport()
        val requests = requests(transport)
        val failure = IllegalStateException("send failed")
        transport.onSend = { throw failure }

        val reported = assertThrows<IllegalStateException> {
            requests.await(responseEndpoint) { transport.sendServerboundPacket(requestEndpoint, Request) }
        }
        assertEquals(failure.message, reported.message)
        assertTrue(generateSequence<Throwable>(reported) { it.cause }.any { it === failure })
        transport.onSend = { requests.onResponse(responseEndpoint, Response("retry")) }

        assertEquals("retry", requests.await(responseEndpoint) {
            transport.sendServerboundPacket(requestEndpoint, Request)
        }.value)
        requests.close()
    }

    @Test
    fun timingOutTheCallerRemovesItsRequestBeforeRetry() = runBlocking {
        val requests = requests()

        assertNull(withTimeoutOrNull(10) { requests.await(responseEndpoint) })
        requests.onResponse(responseEndpoint, Response("late"))
        val next = startWaiting(requests)
        requests.onResponse(responseEndpoint, Response("retry"))

        assertEquals("retry", next.await().value)
        assertTrue(currentCoroutineContext().isActive)
        requests.close()
    }

    @Test
    fun closingConcurrentlyWithAResponseAlwaysCompletesTheRequest() = runBlocking {
        repeat(50) {
            val requests = requests()
            val pending = startWaiting(requests)
            val closing = async(Dispatchers.Default) { requests.close() }
            val responding = async(Dispatchers.Default) { requests.onResponse(responseEndpoint, Response("joined")) }

            closing.await()
            responding.await()
            pending.join()

            assertTrue(pending.isCompleted)
            assertTrue(currentCoroutineContext().isActive)
        }
    }

    @Test
    fun duplicateResponseCannotOverwriteACompletedRequest() = runBlocking {
        val requests = requests()
        val pending = startWaiting(requests)

        requests.onResponse(responseEndpoint, Response("first"))
        requests.onResponse(responseEndpoint, Response("duplicate"))

        assertEquals("first", pending.await().value)
        assertFalse(pending.isCancelled)
        requests.close()
    }

    private fun requests(transport: TestTransport = TestTransport()) =
        ClientRequestHandler(transport, Dispatchers.Unconfined).apply {
            register(responseEndpoint)
        }

    private fun CoroutineScope.startWaiting(requests: ClientRequestHandler) =
        async(start = CoroutineStart.UNDISPATCHED) { requests.await(responseEndpoint) }

    private class TestTransport : ClientTransport {
        var onSend: () -> Unit = {}

        override fun unregisterAll() = Unit

        override fun <P : Packet> registerEndpoint(endpoint: Endpoint<P>, handler: ClientPacketHandler<P>) = Unit

        override fun <P : Packet> sendServerboundPacket(endpoint: Endpoint<P>, packet: P) = onSend()
    }

    @Serializable
    object Request : Packet

    @Serializable
    data class Response(val value: String) : Packet

    companion object {
        private val requestEndpoint = Endpoint<Request>()
        private val responseEndpoint = Endpoint<Response>()
    }
}
