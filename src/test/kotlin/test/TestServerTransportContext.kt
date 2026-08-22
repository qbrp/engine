package org.lain.engine.test

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.server.EngineServer
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.ServerPacketHandler
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.util.nextIdFast

data class SentPacket<P : Packet>(val endpoint: Endpoint<P>, val packet: P)

class TestServerTransportContext(
    private val server: EngineServer
) : ServerTransportContext {
    val sentPackets = mutableListOf<SentPacket<*>>()

    override fun <P : Packet> registerServerReceiver(
        endpoint: Endpoint<P>,
        handler: ServerPacketHandler<P>
    ) {
    }

    override fun unregisterAll() {}

    override fun <P : Packet> sendClientboundPacket(
        endpoint: Endpoint<P>,
        packet: P,
        player: PlayerId,
        id: Long
    ) {
        sentPackets += SentPacket(endpoint, packet)
    }

    override fun <P : Packet> broadcastClientboundPacket(
        endpoint: Endpoint<P>,
        lazyPacket: (EnginePlayer) -> P
    ) {
        server.playerStorage.all.forEach {
            sendClientboundPacket(
                endpoint, lazyPacket(it), it.id,
                nextIdFast()
            )
        }
    }

    override fun isOnThread(): Boolean = true

    override fun executeOnThread(runnable: () -> Unit) {
        TODO()
    }
}