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
    private val channels = mutableSetOf<Endpoint<*>>()
    private val ctx = ClientContext(engine)

    override fun unregisterAll() {
        channels.forEach { ClientPlayNetworking.unregisterGlobalReceiver(it.minecraftIdentifier) }
        channels.clear()
    }

    override fun <P : Packet> registerEndpoint(
        endpoint: Endpoint<P>,
        handler: ClientPacketHandler<P>
    ) {
        check(ClientPlayNetworking.registerGlobalReceiver(PayloadRegistry.payloadOf(endpoint)) { payload, _ ->
            if (endpoint in channels) {
                payload.packet.handler(ctx)
            }
        }) {
            "Обработчик пакета ${endpoint.identifier} уже зарегистрирован"
        }
        channels += endpoint
    }

    override fun <P : Packet> sendServerboundPacket(
        endpoint: Endpoint<P>,
        packet: P
    ) {
        val payload = PayloadRegistry.payloadOf(endpoint)
        if (MinecraftClient.connection == null) return
        ClientPlayNetworking.send(
            EnginePayload(
                nextIdFast(),
                packet,
                payload
            )
        )
    }
}
