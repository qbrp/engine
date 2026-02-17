package org.lain.engine.transport.network

import net.minecraft.server.MinecraftServer
import org.lain.engine.mc.registerServerReceiverInternal
import org.lain.engine.mc.sendClientboundPacketInternal
import org.lain.engine.mc.unregisterServerReceiverInternal
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerStorage
import org.lain.engine.server.DesynchronizationException
import org.lain.engine.transport.*

class ServerNetworkTransport(
    private val server: MinecraftServer,
    private val connectionManager: ServerConnectionManager,
    private val playerStorage: PlayerStorage
) : ServerTransportContext {
    private val endpoints = mutableSetOf<Endpoint<*>>()

    override fun <P : Packet> sendClientboundPacket(endpoint: Endpoint<P>, packet: P, player: PlayerId, id: Long) {
        sendClientboundPacketInternal(endpoint, player, packet, id)
    }

    override fun <P : Packet> broadcastClientboundPacket(
        endpoint: Endpoint<P>,
        lazyPacket: (EnginePlayer) -> P
    ) {
        playerStorage.forEach { player ->
            sendClientboundPacket(endpoint, lazyPacket(player), player.id)
        }
    }

    override fun isOnThread(): Boolean {
        return server.isOnThread()
    }

    override fun executeOnThread(runnable: () -> Unit) {
        if (server.isOnThread()) {
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
                    val message = when(fail) {
                        is DesynchronizationException -> "Рассинхронизация: ${fail.message!!}.<newline>Перезайдите в игру. В случае, если ошибка продолжает появляться, свяжитесь с администраторами"
                        else -> fail.message ?: "Неизвестная ошибка"
                    }
                    connectionManager.disconnect(session, message)
                }
            }
        }
    }
}