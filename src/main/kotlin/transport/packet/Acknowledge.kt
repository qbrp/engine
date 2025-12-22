package org.lain.engine.transport.packet

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel as KotlinxChannel
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketCodec
import org.lain.engine.transport.ServerTransportContext
import kotlin.collections.set

data class AcknowledgePacket(val id: Long) : Packet

internal fun Endpoint(id: String) = Endpoint(
    id,
    PacketCodec.Binary(
        { AcknowledgePacket(readLong()) },
        { writeLong(it.id) }
    )
)

val ACKNOWLEDGE_REQUEST_CHANNEL = Endpoint("acknowledge_request")
val ACKNOWLEDGE_CONFIRM_CHANNEL = Endpoint("acknowledge_confirm")

object GlobalAcknowledgeListener {
    private val listeners: MutableMap<Long, () -> Unit> = mutableMapOf()
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

    suspend fun run() = withContext(Dispatchers.IO) {
        val receiveChannel = KotlinxChannel<Unit>(capacity = 1)
        var timeout = false
        GlobalAcknowledgeListener.addListener(id) { receiveChannel.trySend(Unit) }

        val senderJob = launch {
            var attempts = 0
            while (isActive && attempts < retryAttempts) {
                transport.sendClientboundPacket(
                    ACKNOWLEDGE_REQUEST_CHANNEL,
                    AcknowledgePacket(id),
                    player
                )

                delay(retryTime.toLong())
                attempts++
            }
            if (!timeout) {
                timeout = true
                onTimeout()
                receiveChannel.close()
            }
        }

        try {
            receiveChannel.receive() // ждём подтверждение
        } catch (e: ClosedReceiveChannelException) {
        } finally {
            timeout = true
            GlobalAcknowledgeListener.removeListener(id)
            receiveChannel.close()
            senderJob.cancel() // останавливаем отправку
        }
    }
}