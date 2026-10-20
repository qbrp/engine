package org.lain.engine.mc

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.deserializePacket
import org.lain.engine.transport.network.MinecraftConnection
import org.lain.engine.transport.serializePacket
import java.util.concurrent.ConcurrentHashMap

typealias ServerPacketHandlerInternal<P> = (MinecraftConnection, MinecraftServer, P) -> Unit

fun <P : Packet> registerServerReceiverInternal(
    channel: Endpoint<P>,
    handler: ServerPacketHandlerInternal<P>,
) {
    val payloadId = PayloadRegistry.payloadOf(channel)

    ServerPlayNetworking.registerGlobalReceiver(payloadId) { payload, context ->
        val packet = payload.packet
        val player = context.player()
        handler(player.connection, player.server, packet)
    }
}

fun unregisterServerReceiverInternal(channel: Endpoint<*>) {
    ServerPlayNetworking.unregisterGlobalReceiver(channel.minecraftIdentifier)
}

typealias PayloadId<T> = CustomPacketPayload.Type<T>
typealias Payload = CustomPacketPayload

data class EnginePayload<P : Packet>(
    val packetId: Long,
    val packet: P,
    val payloadId: PayloadId<EnginePayload<P>>
) : Payload {
    override fun type(): PayloadId<out Payload> = payloadId
}

object PayloadRegistry {
    private val map: ConcurrentHashMap<String, PayloadId<*>> = ConcurrentHashMap()

    @Suppress("UNCHECKED_CAST")
    fun <P : Packet> payloadOf(endpoint: Endpoint<P>): PayloadId<EnginePayload<P>> {
        map[endpoint.identifier]?.let { return it as PayloadId<EnginePayload<P>> }
        val s2c = PayloadTypeRegistry.playS2C().registerPayload(endpoint)
        try {
            PayloadTypeRegistry.playC2S().registerPayload(endpoint)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        map[endpoint.identifier] = s2c
        return s2c
    }

    fun all() = map.values
}

val Endpoint<*>.minecraftIdentifier: ResourceLocation
    get() = engineId(identifier.lowercase())

fun <P : Packet> PayloadTypeRegistry<RegistryFriendlyByteBuf>.registerPayload(endpoint: Endpoint<P>): PayloadId<EnginePayload<P>> {
    val payloadId = PayloadId<EnginePayload<P>>(endpoint.minecraftIdentifier)
    register(
        payloadId,
        StreamCodec<RegistryFriendlyByteBuf, EnginePayload<P>>.of(
            { buf, payload ->
                buf.writeLong(payload.packetId)
                serializePacket(buf, payload.packet, endpoint.codec)
            },
            { buf ->
                EnginePayload(buf.readLong(), deserializePacket(buf, endpoint.codec), payloadId)
            }
        )
    )
    return payloadId
}

fun DisconnectText(reason: String) = "<red>[ENGINE] ${reason}</red>".parseMiniMessageLegacy()

fun DisconnectText(exception: Throwable) = DisconnectText(exception.message ?: "Unknown error")
