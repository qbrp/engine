package org.lain.engine.transport.network

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import org.lain.engine.mc.EnginePayload
import org.lain.engine.mc.PayloadRegistry
import org.lain.engine.mc.commands.FriendlyException
import org.lain.engine.mc.registerServerReceiverInternal
import org.lain.engine.mc.unregisterServerReceiverInternal
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.*

class ServerNetworkTransport(
    private val server: MinecraftServer,
    private val connectionManager: ServerConnectionManager,
) : ServerTransportContext {
    private val endpoints = mutableSetOf<Endpoint<*>>()

    override fun <P : Packet> sendClientboundPacket(endpoint: Endpoint<P>, packet: P, player: PlayerId, id: Long) {
        val payload = EnginePayload(
            id,
            packet,
            PayloadRegistry.payloadOf(endpoint),
        )
        val session = connectionManager.getSession(player)
        session.connection.send(ServerPlayNetworking.createS2CPacket(payload))
    }

    override fun <P : Packet> broadcastClientboundPacket(
        endpoint: Endpoint<P>,
        lazyPacket: (EnginePlayer) -> P
    ) {
        connectionManager.iterateSessions().forEach { session ->
            val player = session.player ?: return@forEach
            sendClientboundPacket(endpoint, lazyPacket(player), session.playerId)
        }
    }

    override fun isOnThread(): Boolean {
        return server.isSameThread
    }

    override fun executeOnThread(runnable: () -> Unit) {
        if (server.isSameThread) {
            runnable()
        } else {
            server.execute(runnable)
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
        endpoints += endpoint
        registerServerReceiverInternal(endpoint) { minecraftConnection, server, packet ->
            server.execute {
                val connection = connectionManager.getSessionOrNull(minecraftConnection)
                    ?: return@execute
                try {
                    packet.handler(ServerPacketContext(connection.playerId))
                } catch (e: Exception) {
                    // немедленно кикаем игрока
                    connectionManager.disconnect(connection, e)

                    if (e !is FriendlyException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
