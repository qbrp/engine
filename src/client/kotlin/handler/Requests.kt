package org.lain.engine.client.handler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.lain.engine.client.transport.ClientTransport
import org.lain.engine.client.util.withClientContext
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

class ClientRequestHandler(
    private val transport: ClientTransport,
    private val scope: CoroutineScope
) {
    data class Ack<P : Packet>(val channel: String, val packet: P)

    private val endpoints = ConcurrentHashMap<KClass<out Packet>, Endpoint<out Packet>>()
    private val awaits = mutableMapOf<String, CompletableDeferred<out Packet>>()
    private val packetChannel = Channel<Ack<out Packet>>()

    @Suppress("UNCHECKED_CAST")
    private fun <P : Packet> endpointOf(kclass: KClass<P>): Endpoint<P> {
        return endpoints[kclass] as? Endpoint<P> ?: error("No endpoint found for $kclass")
    }

    fun <P : Packet> register(endpoint: Endpoint<P>) {
        endpoints[endpoint.kclass] = endpoint
        transport.registerEndpoint(endpoint) {
            val packet = this
            runBlocking { packetChannel.send(Ack(endpoint.identifier, packet)) }
        }
    }

    fun run() {
        scope.launch {
            try {
                while (true) {
                    val ack = packetChannel.receive()
                    val deferred = awaits[ack.channel] as? CompletableDeferred<Packet>
                    deferred?.complete(ack.packet)
                }
            } catch (e: CancellationException) {

            }
        }
    }

    fun close() {
        scope.cancel()
        packetChannel.close()
        awaits.values.forEach { it.cancel() }
    }

    suspend fun <I : Packet, O : Packet> request(packet: I?, packetKClass: KClass<O>): Deferred<O> = withClientContext {
        val outputEndpoint = endpointOf(packetKClass)
        val await = CompletableDeferred<O>()
        awaits[outputEndpoint.identifier] = await
        packet?.let {
            val inputEndpoint = endpointOf(packet::class) as Endpoint<Packet>
            transport.sendServerboundPacket(inputEndpoint, it)
        }
        await
    }

    @Suppress("UNCHECKED_CAST")
    suspend inline fun <reified O : Packet> request(packet: Packet?): Deferred<O> {
        return request(packet, O::class)
    }

    suspend inline fun <reified O : Packet> await(packet: Packet?): O {
        return withTimeout(5.seconds) { request(packet, O::class).await() }
    }
}