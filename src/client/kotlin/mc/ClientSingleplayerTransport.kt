package org.lain.engine.client.mc

import org.lain.engine.client.EngineClient
import org.lain.engine.client.transport.ClientContext
import org.lain.engine.client.transport.ClientPacketHandler
import org.lain.engine.client.transport.ClientTransport
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.server.EngineServer
import org.lain.engine.transport.*
import org.lain.engine.util.nextIdFast

private data class Listener<P : Packet>(
    val endpoint: Endpoint<P>,
    val handler: (P, Long) -> Unit
)

private typealias ListenerMap = MutableMap<String, Listener<*>>

private fun <P : Packet> ListenerMap.invoke(endpoint: Endpoint<P>, packet: P, id: Long) {
    val identifier = endpoint.identifier
    val listener = this[identifier]?.handler as? (P, Long) -> Unit
    listener?.invoke(packet, id) ?: error("Unknown listener $identifier")
}

private fun <P : Packet> ListenerMap.register(endpoint: Endpoint<P>, listener: (P, Long) -> Unit) {
    this[endpoint.identifier] = Listener(endpoint, listener)
}

private object CommonSingleplayerEndpointRegistry {
    val serverListeners = mutableMapOf<String, Listener<*>>()
    val clientListeners = mutableMapOf<String, Listener<*>>()
}

class ClientSingleplayerTransport(
    private val client: EngineClient
) : ClientTransport {
    private val context = ClientContext(client)

    override fun unregisterAll() {
        CommonSingleplayerEndpointRegistry.clientListeners.clear()
    }

    override fun <P : Packet> registerEndpoint(
        endpoint: Endpoint<P>,
        handler: ClientPacketHandler<P>
    ) {
        CommonSingleplayerEndpointRegistry.clientListeners.register(endpoint) { packet, id -> handler.invoke(packet, context) }
    }

    override fun <P : Packet> sendServerboundPacket(
        endpoint: Endpoint<P>,
        packet: P
    ) {
        CommonSingleplayerEndpointRegistry.serverListeners.invoke(endpoint, packet, nextIdFast())
    }
}

class ServerSingleplayerTransport(
    private val client: EngineClient,
    private val server: EngineServer
) : ServerTransportContext {
    private val mainPlayer
        get() = client.gameSession?.mainPlayer

    override fun <P : Packet> registerServerReceiver(
        endpoint: Endpoint<P>,
        handler: ServerPacketHandler<P>
    ) {
        CommonSingleplayerEndpointRegistry.serverListeners.register(endpoint) { packet, id ->
            if (server.stopped) return@register
            executeOnThread {
                val plr = mainPlayer ?: return@executeOnThread
                val context = ServerPacketContext(plr.id)
                handler.invoke(packet, context)
            }
        }
    }

    override fun unregisterAll() {
        CommonSingleplayerEndpointRegistry.serverListeners.clear()
    }

    override fun <P : Packet> sendClientboundPacket(
        endpoint: Endpoint<P>,
        packet: P,
        player: PlayerId,
        id: Long
    ) {
        if (player == mainPlayer?.id || !packet.requireAuthorized) {
            CommonSingleplayerEndpointRegistry.clientListeners.invoke(endpoint, packet, id)
        }
    }


    override fun <P : Packet> broadcastClientboundPacket(
        endpoint: Endpoint<P>,
        lazyPacket: (EnginePlayer) -> P
    ) {
        val plr = mainPlayer ?: return
        sendClientboundPacket(endpoint, lazyPacket(plr), plr.id)
    }

    override fun isOnThread(): Boolean = server.isOnThread()

    override fun executeOnThread(runnable: () -> Unit) = server.execute(runnable)
}