package org.lain.engine.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import org.lain.engine.Constants
import org.lain.engine.debugPacket
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.packet.InputPacket
import org.lain.engine.util.injectServerTransportContext
import org.lain.engine.util.math.randomLong
import org.lain.engine.util.nextIdFast
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

    fun sendS2C(packet: P, player: PlayerId, id: Long = nextIdFast()) = executeOnThread {
        if (Constants.SIMULATE_LATENCY) {
            val endpoint = this
            CoroutineScope(Dispatchers.IO).launch {
                delay(randomLong(250))
                transport.sendClientboundPacket(endpoint, packet, player, id)
            }
        } else {
            transport.sendClientboundPacket(this, packet, player, id)
        }
    }

    fun sendAllS2C(packets: List<P>, player: PlayerId) = executeOnThread {
        packets.forEach { sendS2C(it, player) }
    }

    fun registerReceiver(handler: ServerPacketHandler<P>) = executeOnThread {
        transport.registerServerReceiver(this) {
            handler(this, it)
            if (this !is InputPacket) {
                debugPacket("[Сервер] Принят пакет $this")
            }
        }
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
        id: Long = nextIdFast()
    )

    fun <P : Packet> broadcastClientboundPacket(
        endpoint: Endpoint<P>,
        lazyPacket: (EnginePlayer) -> P,
    )

    fun isOnThread(): Boolean
    fun executeOnThread(runnable: () -> Unit)
}

// Packet


interface Packet {
    val requireAuthorized: Boolean
        get() = true
}

// Context

interface PacketContext

data class ServerPacketContext(val sender: PlayerId) : PacketContext

//// Registration

typealias PacketHandler<C, P> = P.(C) -> Unit

typealias ServerPacketHandler<P> = PacketHandler<ServerPacketContext, P>
