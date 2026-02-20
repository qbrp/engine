package org.lain.engine.transport

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import net.minecraft.network.PacketByteBuf

sealed class PacketCodec<P : Packet> {
    class Kotlinx<P : Packet>(
        val serializer: KSerializer<P>,
        val format: BinaryFormat? = null,
    ) : PacketCodec<P>()
    class Binary<P : Packet>(
        val deserializer: PacketByteBuf.() -> P,
        val serializer: PacketByteBuf.(P) -> Unit
    ) : PacketCodec<P>()
}

@OptIn(ExperimentalSerializationApi::class)
fun <P : Packet> deserializePacket(buf: PacketByteBuf, codec: PacketCodec<P>): P {
    return when (codec) {
        is PacketCodec.Binary<P> -> {
            codec.deserializer.invoke(buf)
        }

        is PacketCodec.Kotlinx<P> -> {
            (codec.format ?: ProtoBuf).decodeFromByteArray(codec.serializer, buf.readByteArray())
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun <P : Packet> serializePacket(buf: PacketByteBuf, packet: P, codec: PacketCodec<P>) {
    when (codec) {
        is PacketCodec.Binary<P> -> {
            codec.serializer.invoke(buf, packet)
        }

        is PacketCodec.Kotlinx<P> -> {
            buf.writeByteArray(
                (codec.format ?: ProtoBuf).encodeToByteArray(codec.serializer, packet)
            )
        }
    }
}