package org.lain.engine.transport.packet

import kotlinx.coroutines.*
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketCodec
import org.lain.engine.transport.ServerTransportContext
import java.util.concurrent.ConcurrentHashMap

data class AcknowledgePacket(val id: Long) : Packet

internal fun AcknowledgeEndpoint(id: String) = Endpoint(
    id,
    PacketCodec.Binary(
        { AcknowledgePacket(readLong()) },
        { writeLong(it.id) }
    )
)

val ACKNOWLEDGE_REQUEST_CHANNEL = AcknowledgeEndpoint("acknowledge_request")
val ACKNOWLEDGE_CONFIRM_CHANNEL = AcknowledgeEndpoint("acknowledge_confirm")

object GlobalAcknowledgeListener {
    private val listeners: ConcurrentHashMap<Long, () -> Unit> = ConcurrentHashMap()
    private var lock = Any()

    fun addListener(id: Long, onReceive: () -> Unit) = synchronized(lock) {
        listeners[id] = onReceive
    }

    fun removeListener(id: Long) = synchronized(lock) {
        listeners.remove(id)
    }

    internal fun confirm(id: Long) {
        listeners.remove(id)?.invoke()
    }

    fun start() {
        ACKNOWLEDGE_CONFIRM_CHANNEL
            .registerReceiver { confirm(id) }
    }
}

data class PacketNotAcknowledgedException(
    val packet: Packet,
    val id: Long
) : RuntimeException("Пакет ${packet::class.simpleName} не подтвержден. ID: $id ")

class ServerAcknowledgeTask(
    private val id: Long,
    private val packet: Packet,
    private val player: PlayerId,
    private val transport: ServerTransportContext,
    val retryAttempts: Int,
    val retryTime: Int,
) {
    private var onTimeout: () -> Unit = {
        throw PacketNotAcknowledgedException(packet, id)
    }

    fun onTimeout(b: () -> Unit): ServerAcknowledgeTask {
        onTimeout = b
        return this
    }

    fun onTimeoutServerThread(b: () -> Unit): ServerAcknowledgeTask {
        onTimeout = { transport.executeOnThread(b) }
        return this
    }

    suspend fun run() = coroutineScope {
        val deferred = CompletableDeferred<Unit>()

        GlobalAcknowledgeListener.addListener(id) {
            deferred.complete(Unit)
        }

        val senderJob = launch {
            repeat(retryAttempts) {
                transport.sendClientboundPacket(
                    ACKNOWLEDGE_REQUEST_CHANNEL,
                    AcknowledgePacket(id),
                    player
                )
                delay(retryTime.toLong())
            }
        }

        try {
            withTimeout(retryAttempts * retryTime.toLong()) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            onTimeout()
        } finally {
            GlobalAcknowledgeListener.removeListener(id)
            senderJob.cancel()
        }
    }
}