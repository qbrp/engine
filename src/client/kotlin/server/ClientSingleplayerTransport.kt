package org.lain.engine.client.server

import org.lain.engine.client.EngineClient
import org.lain.engine.client.transport.ClientContext
import org.lain.engine.client.transport.ClientPacketHandler
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.ServerPacketContext
import org.lain.engine.transport.ServerPacketHandler
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.packet.JoinGamePacket
import org.lain.engine.util.FixedSizeList
import org.lain.engine.util.nextId

private enum class Side { CLIENT, SERVER }

private object CommonSingleplayerEndpointRegistry {
    private data class Listener<P : Packet>(
        val endpoint: Endpoint<P>,
        val handler: (P, Long) -> Unit
    )

    private val listeners = mapOf(
        Side.SERVER to mutableMapOf<String, Listener<*>>(),
        Side.CLIENT to mutableMapOf<String, Listener<*>>()
    )

    fun <P : Packet> register(endpoint: Endpoint<P>, side: Side, listener: (P, Long) -> Unit) {
        listeners[side]!![endpoint.identifier] = Listener(endpoint, listener)
    }

    @Suppress("UNCHECKED_CAST")
    fun <P : Packet> invoke(endpoint: Endpoint<P>, side: Side, packet: P, id: Long) {
        (listeners[side]!![endpoint.identifier]?.handler as? (P, Long) -> Unit)?.invoke(packet, id)
    }

    fun unregisterAll(side: Side) {
        listeners[side]!!.clear()
    }
}

class ClientSingleplayerTransport(
    private val client: EngineClient
) : ClientTransportContext {
    private val context = ClientContext(client)
    override val packetHistory: FixedSizeList<Long> = FixedSizeList(3000)

    override fun unregisterAll() {
        CommonSingleplayerEndpointRegistry.unregisterAll(Side.CLIENT)
    }

    override fun <P : Packet> registerEndpoint(
        endpoint: Endpoint<P>,
        handler: ClientPacketHandler<P>
    ) {
        CommonSingleplayerEndpointRegistry.register(endpoint, Side.CLIENT) { packet, id ->
            packetHistory.add(id)
            handler.invoke(packet, context)
        }
    }

    override fun <P : Packet> sendServerboundPacket(
        endpoint: Endpoint<P>,
        packet: P
    ) {
        CommonSingleplayerEndpointRegistry.invoke(endpoint, Side.SERVER, packet, nextId())
    }
}

class ServerSingleplayerTransport(
    private val client: EngineClient
) : ServerTransportContext {
    private val mainPlayer
        get() = client.gameSession?.mainPlayer

    override fun <P : Packet> registerServerReceiver(
        endpoint: Endpoint<P>,
        handler: ServerPacketHandler<P>
    ) {
        CommonSingleplayerEndpointRegistry.register(endpoint, Side.SERVER) { packet, id ->
            val plr = mainPlayer ?: return@register
            val context = ServerPacketContext(plr.id)
            handler.invoke(packet, context)
        }
    }

    override fun unregisterAll() {
        CommonSingleplayerEndpointRegistry.unregisterAll(Side.SERVER)
    }

    override fun <P : Packet> sendClientboundPacket(
        endpoint: Endpoint<P>,
        packet: P,
        player: PlayerId,
        id: Long
    ) {
        if (player == mainPlayer?.id || packet is JoinGamePacket) {
            CommonSingleplayerEndpointRegistry.invoke(endpoint, Side.CLIENT, packet, id)
        }
    }

    override fun <P : Packet> broadcastClientboundPacket(
        endpoint: Endpoint<P>,
        lazyPacket: (EnginePlayer) -> P
    ) {
        val plr = mainPlayer ?: return
        sendClientboundPacket(endpoint, lazyPacket(plr), plr.id)
    }
}