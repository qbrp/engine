package org.lain.engine.client.mc

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen
import org.lain.engine.client.transport.ClientContext
import org.lain.engine.client.transport.ClientPacketHandler
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.mc.DisconnectText
import org.lain.engine.mc.EnginePayload
import org.lain.engine.mc.PayloadRegistry
import org.lain.engine.mc.minecraftIdentifier
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.util.FixedSizeList
import org.lain.engine.util.nextId

class ClientMinecraftNetwork : ClientTransportContext {
    override val packetHistory: FixedSizeList<Long> = FixedSizeList(3000)
    private val engine by injectClient()
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
            packetHistory.add(payload.packetId)
            val client = context.client()
            val packet = payload.packet
            //println("Принят пакет $packet, ID: ${payload.packetId}")

            client.execute {
                if (client.world == null) return@execute
                try {
                    with(ctx) {
                        packet.handler(ctx)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    client.disconnect(DisconnectText(e.message ?: "Unknown error"))
                }
            }
        }
    }

    override fun <P : Packet> sendServerboundPacket(
        endpoint: Endpoint<P>,
        packet: P
    ) {
        val payload = PayloadRegistry.payloadOf(endpoint)
        ClientPlayNetworking.send(
            EnginePayload(
                nextId(),
                packet,
                payload
            )
        )
    }
}