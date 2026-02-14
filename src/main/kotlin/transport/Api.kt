package org.lain.engine.transport

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.packet.ServerAcknowledgeTask
import org.lain.engine.util.injectServerTransportContext
import org.lain.engine.util.nextId
import kotlin.reflect.KClass

class Endpoint<P : Packet>(
    val identifier: String,
    val codec: PacketCodec<P>
) {
    private val transport by injectServerTransportContext()

    private fun executeOnThread(runnable: () -> Unit) {
        if (transport.isOnThread()) {
            runnable()
        } else {
            transport.executeOnThread(runnable)
        }
    }

    fun sendS2C(packet: P, player: PlayerId) = executeOnThread {
        transport.sendClientboundPacket(this, packet, player)
    }

    fun sendAllS2C(packets: List<P>, player: PlayerId) = executeOnThread {
        packets.forEach { sendS2C(it, player) }
    }

    fun taskS2C(packet: P, player: PlayerId, id: Long = nextId()): ServerPacketSendTask<P> {
        return ServerPacketSendTask(id, packet, this, transport, player)
    }

    fun registerReceiver(handler: ServerPacketHandler<P>) = executeOnThread {
        transport.registerServerReceiver(this, handler)
    }

    fun broadcast(packet: P) = executeOnThread {
        transport.broadcastClientboundPacket(this) { packet }
    }

    fun broadcast(lazyPacket: (EnginePlayer) -> P) = executeOnThread {
        transport.broadcastClientboundPacket(this, lazyPacket)
    }
}

@OptIn(InternalSerializationApi::class)
inline fun <reified P : Packet> Endpoint(
    codec: PacketCodec<P> = PacketCodec.Kotlinx(P::class.serializer())
): Endpoint<P> {
    return Endpoint(P::class.channelName, codec)
}

@OptIn(InternalSerializationApi::class)
inline fun <reified P : Packet> Endpoint(
    name: String
): Endpoint<P> {
    return Endpoint(name, PacketCodec.Kotlinx(P::class.serializer()))
}

val KClass<out Packet>.channelName
    get() = simpleName?.lowercase() ?: error("Unknown packet class name")

/**
 * **Изначально не потокобезопасен.** Проверку делать через `isOnThread` и `executeOnThread`.
 * Потокобезопасная логика встроена в `Endpoint`
 */
interface ServerTransportContext {
    fun <P : Packet> registerServerReceiver(endpoint: Endpoint<P>, handler: ServerPacketHandler<P>)

    fun unregisterAll()

    fun <P : Packet> sendClientboundPacket(
        endpoint: Endpoint<P>,
        packet: P,
        player: PlayerId,
        id: Long = nextId()
    )

    fun <P : Packet> broadcastClientboundPacket(
        endpoint: Endpoint<P>,
        lazyPacket: (EnginePlayer) -> P,
    )

    fun isOnThread(): Boolean
    fun executeOnThread(runnable: () -> Unit)
}

class ServerPacketSendTask<P : Packet>(
    val id: Long,
    private val packet: P,
    private val endpoint: Endpoint<P>,
    private val transport: ServerTransportContext,
    private val player: PlayerId
) {
    fun send(): ServerPacketSendTask<P> {
        transport.sendClientboundPacket(endpoint, packet, player, id)
        return this
    }

    suspend fun requestAcknowledge(
        retryAttempts: Int = 10,
        retryTime: Int = 500
    ) = withAcknowledge(retryAttempts, retryTime)
            .also { it.run() }

    fun withAcknowledge(
        retryAttempts: Int = 10,
        retryTime: Int = 500
    ): ServerAcknowledgeTask {
        return ServerAcknowledgeTask(
            id,
            packet,
            player,
            transport,
            retryAttempts,
            retryTime
        )
    }
}

// Packet


interface Packet

// Context

interface PacketContext

data class ServerPacketContext(val sender: PlayerId) : PacketContext

//// Registration

typealias PacketHandler<C, P> = P.(C) -> Unit

typealias ServerPacketHandler<P> = PacketHandler<ServerPacketContext, P>
