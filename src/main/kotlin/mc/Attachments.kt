
package org.lain.engine.mc

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.codec.PacketDecoder
import net.minecraft.util.math.BlockPos
import net.minecraft.world.chunk.Chunk
import org.lain.engine.util.EngineId
import org.lain.engine.world.*

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

var BLOCK_HINT_ATTACHMENT_TYPE: AttachmentType<ChunkBlockHints>? = null

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
    }.also { BLOCK_HINT_ATTACHMENT_TYPE = it }
}

var BLOCK_DECALS_ATTACHMENT_TYPE: AttachmentType<Map<BlockPos, BlockDecals>>? = null

fun Chunk.removeBlockDecals(pos: BlockPos) {
    val target = this as AttachmentTarget
    val blockCopy = target.getAttached(BLOCK_DECALS_ATTACHMENT_TYPE)?.toMutableMap() ?: mutableMapOf()
    blockCopy.remove(pos)
    target.setAttached(BLOCK_DECALS_ATTACHMENT_TYPE, blockCopy)
}

fun Chunk.setBlockDecals(pos: BlockPos, decals: BlockDecals) {
    val target = this as AttachmentTarget
    val blockCopy = target.getAttached(BLOCK_DECALS_ATTACHMENT_TYPE)?.toMutableMap() ?: mutableMapOf()
    blockCopy[pos] = decals
    target.setAttached(BLOCK_DECALS_ATTACHMENT_TYPE, blockCopy)
}

fun Chunk.updateBlockDecals(pos: BlockPos, update: (BlockDecals?) -> BlockDecals) {
    val target = this as AttachmentTarget
    val blocksCopy = target.getAttached(BLOCK_DECALS_ATTACHMENT_TYPE)?.toMutableMap() ?: mutableMapOf()
    val oldDecals = blocksCopy[pos]
    blocksCopy[pos] = update(oldDecals)
    target.setAttached(BLOCK_DECALS_ATTACHMENT_TYPE, blocksCopy)
}

val DECAL_CONTENTS_CODEC: Codec<DecalContents> =
    Codec.STRING.dispatch(
        { contents ->
            when (contents) {
                is DecalContents.Chip -> "chip"
            }
        },
        { type ->
            when (type) {
                "chip" -> RecordCodecBuilder.mapCodec { instance ->
                    instance.group(
                        Codec.INT.fieldOf("radius")
                            .forGetter { (it as DecalContents.Chip).radius }
                    ).apply(instance) { DecalContents.Chip(it) }
                }
                else -> error("Unknown DecalContents type: $type")
            }
        }
    )

val DECAL_CODEC: Codec<Decal> =
    RecordCodecBuilder.create { inst ->
        inst.group(
            Codec.INT.fieldOf("x").forGetter(Decal::x),
            Codec.INT.fieldOf("y").forGetter(Decal::y),
            Codec.FLOAT.fieldOf("depth").forGetter(Decal::depth),
            DECAL_CONTENTS_CODEC.fieldOf("contents").forGetter(Decal::contents)
        ).apply(inst, ::Decal)
    }

val DECALS_CODEC: Codec<Decals> = DECAL_CODEC.listOf()

val DIRECTION_CODEC: Codec<Direction> =
    Codec.STRING.xmap(
        Direction::valueOf,
        Direction::name
    )

val DECALS_LAYER_CODEC: Codec<DecalsLayer> =
    RecordCodecBuilder.create { inst ->
        inst.group(
            Codec.unboundedMap(DIRECTION_CODEC, DECALS_CODEC)
                .fieldOf("directions")
                .forGetter(DecalsLayer::directions)
        ).apply(inst, ::DecalsLayer)
    }

val BLOCK_DECALS_CODEC: Codec<BlockDecals> =
    RecordCodecBuilder.create { inst ->
        inst.group(
            Codec.INT.fieldOf("version").forGetter(BlockDecals::version),
            DECALS_LAYER_CODEC.listOf()
                .fieldOf("layers")
                .forGetter(BlockDecals::layers)
        ).apply(inst, ::BlockDecals)
    }

val BLOCK_POS_AS_STRING_CODEC: Codec<BlockPos> =
    Codec.STRING.xmap(
        { str ->
            val (x, y, z) = str.split(',').map(String::toInt)
            BlockPos(x, y, z)
        },
        { pos -> "${pos.x},${pos.y},${pos.z}" }
    )

val BLOCK_DECALS_ATTACHMENT_CODEC: Codec<Map<BlockPos, BlockDecals>> =
    Codec.unboundedMap(BLOCK_POS_AS_STRING_CODEC, BLOCK_DECALS_CODEC)


val DECAL_CONTENTS_PACKET_CODEC = PacketCodecs.INTEGER.dispatch(
    { contents ->
        when (contents) {
            is DecalContents.Chip -> 0
        }
    },
    { type ->
        when (type) {
            0 -> PacketCodecs.INTEGER.xmap<DecalContents>(
                { radius -> DecalContents.Chip(radius) },
                { (it as DecalContents.Chip).radius }
            )
            else -> error("Unknown DecalContents type id: $type")
        }
    }
)

val DECAL_PACKET_CODEC =
    PacketCodec.tuple(
        PacketCodecs.INTEGER, Decal::x,
        PacketCodecs.INTEGER, Decal::y,
        PacketCodecs.FLOAT, Decal::depth,
        DECAL_CONTENTS_PACKET_CODEC, Decal::contents,
        ::Decal
    )

val DECALS_PACKET_CODEC = DECAL_PACKET_CODEC.collect(PacketCodecs.toList())

val DIRECTION_PACKET_CODEC = PacketCodecs.indexed({ Direction.fromIndex(it) }, { it.index })

val DECALS_LAYER_PACKET_CODEC =
    PacketCodecs.map(
        { HashMap(it) },
        DIRECTION_PACKET_CODEC,
        DECALS_PACKET_CODEC
    ).xmap(
        { DecalsLayer(it) },
        { HashMap(it.directions) }
    )

val BLOCK_DECALS_PACKET_CODEC =
    PacketCodec.tuple(
        PacketCodecs.INTEGER, BlockDecals::version,
        DECALS_LAYER_PACKET_CODEC.collect(PacketCodecs.toList()), BlockDecals::layers,
        ::BlockDecals
    )

val BLOCK_DECALS_ATTACHMENT_PACKET_CODEC: PacketCodec<RegistryByteBuf, Map<BlockPos, BlockDecals>> =
    PacketCodecs.map(
        { HashMap(it) },
        BlockPos.PACKET_CODEC,
        BLOCK_DECALS_PACKET_CODEC
    )

fun registerBlockDecalsAttachment() {
    BLOCK_DECALS_ATTACHMENT_TYPE = AttachmentRegistry.create(EngineId("block-decals")) { builder ->
        builder.persistent(BLOCK_DECALS_ATTACHMENT_CODEC)
        builder.syncWith(BLOCK_DECALS_ATTACHMENT_PACKET_CODEC, AttachmentSyncPredicate.all())
    }
}