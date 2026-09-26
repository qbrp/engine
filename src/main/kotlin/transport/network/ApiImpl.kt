package org.lain.engine.transport.network

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.lain.engine.mc.EnginePayload
import org.lain.engine.mc.PayloadRegistry
import org.lain.engine.mc.registerServerReceiverInternal
import org.lain.engine.mc.unregisterServerReceiverInternal
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerStorage
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
                    connectionManager.disconnect(session.playerId, fail)
                }
            }
        }
    }
}