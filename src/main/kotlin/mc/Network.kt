package org.lain.engine.mc

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.ValueFirstEncoder
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.network.ConnectionSession
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.transport.deserializePacket
import org.lain.engine.transport.serializePacket
import org.lain.engine.util.EngineId
import org.lain.engine.util.engineId
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.text.parseMiniMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

typealias ServerPacketHandlerInternal<P> = (ConnectionSession, Executor, P) -> Unit

fun <P : Packet> registerServerReceiverInternal(
    channel: Endpoint<P>,
    connectionManager: ServerConnectionManager,
    handler: ServerPacketHandlerInternal<P>,
) {

    val payloadId = PayloadRegistry.payloadOf(channel)

    ServerPlayNetworking.registerGlobalReceiver(payloadId) { payload, context ->
        val session = connectionManager.getSession(context.player().engineId)
        val packet = payload.packet
        handler(session, context.server(), packet)
    }
}

fun unregisterServerReceiverInternal(channel: Endpoint<*>) {
    ServerPlayNetworking.unregisterGlobalReceiver(channel.minecraftIdentifier)
}

private val ENTITY_TABLE by injectEntityTable()

fun <P : Packet> sendClientboundPacketInternal(endpoint: Endpoint<P>, player: PlayerId, packet: P, id: Long) {
    val payload = EnginePayload(
        id,
        packet,
        PayloadRegistry.payloadOf(endpoint),
    )
    val player = ENTITY_TABLE.server.getEntity(player) as? ServerPlayerEntity ?: error("Entity for player $player not found")
    ServerPlayNetworking.send(player, payload)
}

data class EnginePayload<P : Packet>(
    val packetId: Long,
    val packet: P,
    val payloadId: CustomPayload.Id<EnginePayload<P>>
) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload?> = payloadId
}

object PayloadRegistry {
    private val map: ConcurrentHashMap<String, CustomPayload.Id<*>> = ConcurrentHashMap()

    @Suppress("UNCHECKED_CAST")
    fun <P : Packet> payloadOf(endpoint: Endpoint<P>): CustomPayload.Id<EnginePayload<P>> {
        map[endpoint.identifier]?.let { return it as CustomPayload.Id<EnginePayload<P>> }
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

val Endpoint<*>.minecraftIdentifier: Identifier
    get() = EngineId(identifier)

fun <P : Packet> PayloadTypeRegistry<RegistryByteBuf>.registerPayload(endpoint: Endpoint<P>): CustomPayload.Id<EnginePayload<P>> {
    val payloadId = CustomPayload.Id<EnginePayload<P>>(endpoint.minecraftIdentifier)
    register(
        payloadId,
        PacketCodec<PacketByteBuf, EnginePayload<P>>.of(
            ValueFirstEncoder { payload, buf ->
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

fun DisconnectText(reason: String) = "<red>[ENGINE] ${reason}</red>".parseMiniMessage()

fun disconnectInternal(playerId: PlayerId, reason: String) {
    val entity = ENTITY_TABLE.server.getEntity(playerId) as? ServerPlayerEntity ?: error("Player entity $playerId not found")
    val networkHandler = entity.networkHandler
    entity.entityWorld.server.execute { networkHandler.disconnect(DisconnectText(reason)) }
}