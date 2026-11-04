package org.lain.engine.client.mc

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import org.lain.engine.client.EngineClient
import org.lain.engine.client.transport.ClientContext
import org.lain.engine.client.transport.ClientPacketHandler
import org.lain.engine.client.transport.ClientTransport
import org.lain.engine.mc.EnginePayload
import org.lain.engine.mc.PayloadRegistry
import org.lain.engine.mc.minecraftIdentifier
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.util.nextIdFast

class ClientNetworkTransport(
    private val engine: EngineClient
) : ClientTransport {
    private val channels: MutableList<Endpoint<*>> = mutableListOf()
    private val ctx = ClientContext(engine)

    override fun unregisterAll() {
        channels.forEach { ClientPlayNetworking.unregisterGlobalReceiver(it.minecraftIdentifier) }
    }

    override fun <P : Packet> registerEndpoint(
        endpoint: Endpoint<P>,
        handler: ClientPacketHandler<P>
    ) {
        channels += endpoint
        ClientPlayNetworking.registerGlobalReceiver(PayloadRegistry.payloadOf(endpoint)) { payload, context ->
            payload.packet.handler(ctx)
        }
    }

    override fun <P : Packet> sendServerboundPacket(
        endpoint: Endpoint<P>,
        packet: P
    ) {
        val payload = PayloadRegistry.payloadOf(endpoint)
        ClientPlayNetworking.send(
            EnginePayload(
                nextIdFast(),
                packet,
                payload
            )
        )
    }
}
