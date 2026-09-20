@file:OptIn(ExperimentalSerializationApi::class)
package org.lain.engine.transport

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.protobuf.ProtoBuf
import net.minecraft.network.FriendlyByteBuf
import org.lain.engine.data.polymorphicComponentSerializer

sealed class PacketCodec<P : Packet> {
    class Kotlinx<P : Packet>(
        val serializer: KSerializer<P>,
        val format: BinaryFormat? = null,
    ) : PacketCodec<P>()
    class Binary<P : Packet>(
        val deserializer: FriendlyByteBuf.() -> P,
        val serializer: FriendlyByteBuf.(P) -> Unit
    ) : PacketCodec<P>()
}

private val Protobuf = ProtoBuf {
    serializersModule = SerializersModule { polymorphicComponentSerializer() }
}

fun <P : Packet> deserializePacket(buf: FriendlyByteBuf, codec: PacketCodec<P>): P {
    return when (codec) {
        is PacketCodec.Binary<P> -> {
            codec.deserializer.invoke(buf)
        }

        is PacketCodec.Kotlinx<P> -> {
            (codec.format ?: Protobuf).decodeFromByteArray(codec.serializer, buf.readByteArray())
        }
    }
}

fun <P : Packet> serializePacket(buf: FriendlyByteBuf, packet: P, codec: PacketCodec<P>) {
    when (codec) {
        is PacketCodec.Binary<P> -> {
            codec.serializer.invoke(buf, packet)
        }

        is PacketCodec.Kotlinx<P> -> {
            buf.writeByteArray(
                (codec.format ?: Protobuf).encodeToByteArray(codec.serializer, packet)
            )
        }
    }
}