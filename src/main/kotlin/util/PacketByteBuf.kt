package org.lain.engine.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import net.minecraft.network.PacketByteBuf
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerStorage
import java.util.UUID

fun PacketByteBuf.writeStringList(strings: List<String>) {
    this.writeVarInt(strings.size)
    for (str in strings) {
        this.writeString(str)
    }
}

fun PacketByteBuf.readStringList(): List<String> {
    val size = this.readVarInt()
    return List(size) { this.readString() }
}

fun PacketByteBuf.writePlayerId(value: PlayerId) {
    writeString(value.value.toString())
}

fun PacketByteBuf.readPlayerId(): PlayerId {
    return PlayerId(UUID.fromString(readString()))
}

@OptIn(ExperimentalSerializationApi::class)
fun <T> PacketByteBuf.writeProtobuf(data: T, serializer: KSerializer<T>) {
    val bytes = ProtoBuf.encodeToByteArray(serializer, data)
    this.writeByteArray(bytes)
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T: Any> PacketByteBuf.readProtobuf(serializer: KSerializer<T>): T {
    return ProtoBuf.decodeFromByteArray<T>(serializer, readByteArray())
}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
inline fun <reified T: Any> PacketByteBuf.readProtobuf(): T {
    return ProtoBuf.decodeFromByteArray<T>(T::class.serializer(), readByteArray())
}

fun PacketByteBuf.writePos(p: Pos) {
    writeFloat(p.x)
    writeFloat(p.y)
    writeFloat(p.z)
}

fun PacketByteBuf.readVec3(): Vec3 {
    return Vec3(readFloat(), readFloat(), readFloat())
}

fun PacketByteBuf.readPos(): Pos {
    return readVec3()
}

inline fun <reified E: Enum<E>> PacketByteBuf.writeEnum(e: Enum<E>) {
    writeEnumConstant(e)
}

inline fun <reified E: Enum<E>> PacketByteBuf.readEnum(): E {
    return readEnumConstant<E>(E::class.java)
}