package org.lain.engine.mc

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.Decoder
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.Encoder
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketDecoder
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.chunk.WorldChunk
import org.lain.engine.player.Player
import org.lain.engine.util.EngineId
import kotlin.collections.iterator

data class BlockHint(
    val title: String,
    val texts: MutableList<String> = mutableListOf()
) {
    fun displayText(index: Int): String {
        val text = texts[index]
        return "<gold>$index:</gold> <gray>$text"
    }
}

typealias ChunkBlockHints = MutableMap<BlockPos, BlockHint>

val WorldChunk.blockHints
    get() = (this as AttachmentTarget).getAttached(TYPE)

fun WorldChunk.getBlockHint(pos: BlockPos) = blockHints?.get(pos)

fun WorldChunk.setBlockHint(pos: BlockPos, text: String, index: Int? = null): BlockHint {
    val hints = blockHints!!
    val hint = hints.getOrPut(pos) { BlockHint(getBlockState(pos).block.name.string) }
    val texts = hint.texts
    when (index) {
        null -> texts.add(text)
        in texts.indices -> texts[index] = text
    }
    return hint
}

fun WorldChunk.detachBlockHint(pos: BlockPos, index: Int) {
    val blockHints = blockHints ?: return
    val texts = blockHints[pos]?.texts ?: return
    texts.removeAt(index)
    if (texts.isEmpty()) {
        blockHints.remove(pos)
    }
}

private val BLOCK_HINT_CODEC: Codec<BlockHint> =
    RecordCodecBuilder.create { inst ->
        inst.group(
            Codec.STRING.fieldOf("title").forGetter { it.title },
            Codec.STRING.listOf().fieldOf("texts").forGetter { it.texts }
        ).apply(inst) { title, texts ->
            BlockHint(title, texts.toMutableList())
        }
    }

val BLOCK_POS_KEY_CODEC: Codec<BlockPos> =
    Codec.STRING.xmap(
        { s ->
            BlockPos.fromLong(s.toLong())
        },
        { pos ->
            BlockPos(pos.x, pos.y, pos.z).asLong().toString()
        }
    )

var TYPE: AttachmentType<ChunkBlockHints>? = null

fun registerBlockHintAttachment() {
    AttachmentRegistry.create(EngineId("block-hint")) { builder ->
        builder.persistent(
            Codec.unboundedMap(
                BLOCK_POS_KEY_CODEC,
                BLOCK_HINT_CODEC
            )
        )
        builder.syncWith(
            PacketCodec.of(
                { hints, buf ->
                    buf.writeVarInt(hints.size)
                    for ((pos, hint) in hints) {
                        buf.writeLong(pos.asLong())
                        buf.writeString(hint.title)

                        buf.writeVarInt(hint.texts.size)
                        for (text in hint.texts) {
                            buf.writeString(text)
                        }
                    }
                },
                PacketDecoder<PacketByteBuf, ChunkBlockHints> { buf ->
                    val size = buf.readVarInt()
                    val map = HashMap<BlockPos, BlockHint>(size)
                    repeat(size) {
                        val pos = BlockPos.fromLong(buf.readLong())
                        val title = buf.readString()

                        val textCount = buf.readVarInt()
                        val texts = ArrayList<String>(textCount)
                        repeat(textCount) {
                            texts += buf.readString()
                        }

                        map[pos] = BlockHint(title, texts)
                    }
                    map
                }
            ),
            AttachmentSyncPredicate.all()
        )
    }.also { TYPE = it }
}