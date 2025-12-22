package org.lain.engine.transport.network

import org.lain.engine.mc.registerServerReceiverInternal
import org.lain.engine.mc.sendClientboundPacketInternal
import org.lain.engine.mc.unregisterServerReceiverInternal
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerStorage
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.ServerPacketContext
import org.lain.engine.transport.ServerPacketHandler
import org.lain.engine.transport.ServerPacketSendTask
import org.lain.engine.transport.ServerTransportContext

class ServerNetworkTransport(
    private val connectionManager: ServerConnectionManager,
    private val playerStorage: PlayerStorage
) : ServerTransportContext {
    private val endpoints = mutableSetOf<Endpoint<*>>()

    override fun <P : Packet> sendClientboundPacket(endpoint: Endpoint<P>, packet: P, player: PlayerId, id: Long) {
        sendClientboundPacketInternal(endpoint, player, packet, id)
    }

    override fun <P : Packet> broadcastClientboundPacket(
        endpoint: Endpoint<P>,
        lazyPacket: (Player) -> P
    ) {
        playerStorage.forEach { player ->
            sendClientboundPacket(endpoint, lazyPacket(player), player.id)
        }
    }

    override fun unregisterAll() {
        endpoints.forEach { endpoint ->
            unregisterServerReceiverInternal(endpoint)
        }
    }

    override fun <P : Packet> registerServerReceiver(
        endpoint: Endpoint<P>,
        handler: ServerPacketHandler<P>
    ) {
        registerServerReceiverInternal(
            endpoint,
            connectionManager
        ) { session, server, packet ->
            server.execute {
                var fail: Throwable? = null

                with(ServerPacketContext(session.playerId)) {
                    val result = runCatching { packet.handler(this) }
                    result.onFailure { error -> fail = error }
                }

                if (fail != null) {
                    fail.printStackTrace()
                    connectionManager.disconnect(session, fail.message ?: "Неизвестная ошибка")
                }
            }
        }
    }
}