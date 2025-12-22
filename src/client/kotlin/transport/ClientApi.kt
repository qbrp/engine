package org.lain.engine.client.transport

import org.lain.engine.client.EngineClient
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketContext
import org.lain.engine.transport.PacketHandler
import org.lain.engine.util.FixedSizeList
import org.lain.engine.util.inject
import org.lain.engine.util.injectValue

typealias ClientPacketHandler<P> = PacketHandler<ClientContext, P>

data class ClientContext(val client: EngineClient) : PacketContext

interface ClientTransportContext {
    val packetHistory: FixedSizeList<Long>
    fun unregisterAll()
    fun <P : Packet> registerEndpoint(endpoint: Endpoint<P>, handler: ClientPacketHandler<P>)
    fun <P : Packet> sendServerboundPacket(endpoint: Endpoint<P>, packet: P)
}

fun <P : Packet> Endpoint<P>.registerClientReceiver(handler: ClientPacketHandler<P>) {
    injectValue<ClientTransportContext>().registerEndpoint(this, handler)
}

fun <P : Packet> Endpoint<P>.sendC2SPacket(packet: P) {
    injectValue<ClientTransportContext>().sendServerboundPacket(this, packet)
}

fun injectClientTransportContext() = inject<ClientTransportContext>()