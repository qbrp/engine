package org.lain.engine.mc

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import net.minecraft.network.FriendlyByteBuf
import org.lain.engine.player.PlayerId
import org.lain.engine.util.math.EVec3
import org.lain.engine.util.math.Pos
import org.lain.engine.util.math.Vec3
import java.util.*

fun FriendlyByteBuf.writeStringList(strings: List<String>) {
    this.writeVarInt(strings.size)
    for (str in strings) {
        this.writeUtf(str)
    }
}

fun FriendlyByteBuf.readStringList(): List<String> {
    val size = this.readVarInt()
    return List(size) { this.readUtf() }
}

fun FriendlyByteBuf.writePlayerId(value: PlayerId) {
    writeUtf(value.value.toString())
}

fun FriendlyByteBuf.readPlayerId(): PlayerId {
    return PlayerId(UUID.fromString(readUtf()))
}

@OptIn(ExperimentalSerializationApi::class)
fun <T> FriendlyByteBuf.writeProtobuf(data: T, serializer: KSerializer<T>) {
    val bytes = ProtoBuf.encodeToByteArray(serializer, data)
    this.writeByteArray(bytes)
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T: Any> FriendlyByteBuf.readProtobuf(serializer: KSerializer<T>): T {
    return ProtoBuf.decodeFromByteArray<T>(serializer, readByteArray())
}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
inline fun <reified T: Any> FriendlyByteBuf.readProtobuf(): T {
    return ProtoBuf.decodeFromByteArray<T>(T::class.serializer(), readByteArray())
}

fun FriendlyByteBuf.writePos(p: Pos) {
    writeFloat(p.x)
    writeFloat(p.y)
    writeFloat(p.z)
}

fun FriendlyByteBuf.readEVec3(): EVec3 {
    return Vec3(readFloat(), readFloat(), readFloat())
}

fun FriendlyByteBuf.readEPos(): Pos {
    return readEVec3()
}

inline fun <reified E: Enum<E>> FriendlyByteBuf.writeEnum(e: Enum<E>) {
    writeEnum(e)
}

inline fun <reified E: Enum<E>> FriendlyByteBuf.readEnum(): E {
    return readEnum<E>(E::class.java)
}