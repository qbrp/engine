package org.lain.engine.client.mc.render

import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.command.OrderedRenderCommandQueue
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.client.texture.TextureManager
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Colors
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.chunk.Chunk
import org.joml.Vector3f
import org.lain.engine.mc.BLOCK_DECALS_ATTACHMENT_TYPE
import org.lain.engine.mc.toMinecraft
import org.lain.engine.util.EngineId
import org.lain.engine.util.alsoForEach
import org.lain.engine.world.BlockDecals
import org.lain.engine.world.DecalContents
import org.lain.engine.world.DecalsLayer
import org.lain.engine.world.Direction
import java.util.*

typealias ChunkBlockDecals = MutableMap<BlockPos, BlockDecals>

data class BlockDecalImageData(val layers: MutableList<DecalsLayerTexture>)

class ChunkDecalsStorage(private val textureManager: TextureManager) {
    private val chunks: MutableMap<ChunkPos, ChunkBlockDecals> = mutableMapOf()
    private val images: MutableMap<Pair<ChunkPos, BlockPos>, BlockDecalImageData> = mutableMapOf()

    fun unload(chunkPos: ChunkPos) {
        val removed = chunks.remove(chunkPos)
        removed?.forEach { blockPos, _ ->
            val image = images.remove(chunkPos to blockPos)
            image?.layers?.forEach {
                textureManager.destroyTexture(it.id)
                it.close()
            }
        }
    }

    fun modify(chunk: Chunk, pos: BlockPos, decals: BlockDecals) {
        val oldBlocksCopy = (chunk as AttachmentTarget).getAttached(BLOCK_DECALS_ATTACHMENT_TYPE)?.toMutableMap() ?: mutableMapOf()
        oldBlocksCopy[pos] = decals
        (chunk as AttachmentTarget).setAttached(BLOCK_DECALS_ATTACHMENT_TYPE, oldBlocksCopy)
        update(chunk, pos)
    }

    fun survey(chunk: Chunk) {
        val blocks = (chunk as AttachmentTarget).getAttached(BLOCK_DECALS_ATTACHMENT_TYPE)?.toMutableMap() ?: mutableMapOf()
        update(
            chunk.pos,
            blocks,
            mutableMapOf()
        )
    }

    fun update(chunk: Chunk, pos: BlockPos) {
        RenderSystem.assertOnRenderThread()
        val chunkPos = chunk.pos
        val blocks = (chunk as AttachmentTarget).getAttached(BLOCK_DECALS_ATTACHMENT_TYPE)?.toMap() ?: emptyMap()
        val oldBlocks = chunks[chunkPos] ?: emptyMap()
        val oldBlockDecals = oldBlocks[pos]
        val newBlockDecals = blocks[pos] ?: return
        update(
            chunkPos,
            mutableMapOf(pos to newBlockDecals),
            mutableMapOf(pos to oldBlockDecals)
        )
    }

    private fun update(chunkPos: ChunkPos, newBlocks: ChunkBlockDecals, oldBlocks: MutableMap<BlockPos, BlockDecals?>) {
        for ((pos, newBlockDecals) in newBlocks) {
            val oldBlockDecals = oldBlocks[pos]
            if (oldBlockDecals?.version != newBlockDecals.version) {
                val key = chunkPos to pos
                var image = images[key]
                val imageLayersCount = image?.layers?.count()
                val decalLayers = newBlockDecals.layers

                if (image == null || imageLayersCount != decalLayers.count()) {
                    image = BlockDecalImageData(
                        decalLayers
                            .map { DecalsLayerTexture(pos) }
                            .alsoForEach { it.register(textureManager) }
                            .toMutableList()
                    ).also { images[key] = it }
                }

                for (index in 0 until decalLayers.size) {
                    val imageLayer = image.layers[index]
                    val decalLayer = decalLayers[index]
                    imageLayer.compile(decalLayer)
                }
            }
        }
    }

    fun getBlockImages() = images.entries

    fun getTextures(chunkPos: ChunkPos, blockPos: BlockPos): Collection<DecalsLayerTexture>? {
        return images[chunkPos to blockPos]?.layers
    }
}

class DecalsLayerTexture(val blockPos: BlockPos) : AutoCloseable {
    // Полная развёртка всех сторон блока
    private val texture = NativeImageBackedTexture("Block Decals ${blockPos.toShortString()}", 48, 32, true)
    val id = EngineId("decals/${blockPos.x}/${blockPos.y}/${blockPos.z}")

    override fun close() {
        texture.close()
    }

    fun register(textureManager: TextureManager) {
        textureManager.registerTexture(id, texture)
    }

    fun compile(layer: DecalsLayer) {
        val image = texture.image ?: return
        image.fillRect(0, 0, 48, 32, 0)
        for ((direction, decals) in layer.directions) {
            val (x0, y0) = direction.getStartPos()

            for (decal in decals) {
                when(val contents = decal.contents) {
                    is DecalContents.Chip -> {
                        val radius = contents.radius
                        val halfRadius = radius / 2
                        val (x0, y0) = x0 - halfRadius to y0 - halfRadius

                        repeat(radius) { i ->
                            repeat(radius) { j ->
                                val px = x0 + decal.x - halfRadius + i
                                val py = y0 + decal.y - halfRadius + j
                                if (px in 0 until 48 && py in 0 until 32) {
                                    image.setColorArgb(px, py, Colors.BLACK)
                                }
                            }
                        }
                    }
                }
            }
        }
        texture.upload()
    }
}

private fun Direction.getStartPos() = when(this) {
    Direction.DOWN -> 0 to 0
    Direction.UP -> 0 to 16
    Direction.NORTH -> 16 to 0
    Direction.SOUTH -> 16 to 16
    Direction.WEST -> 32 to 0
    Direction.EAST -> 32 to 16
}

fun renderBlockDecals(texture: DecalsLayerTexture, blockPos: BlockPos, matrices: MatrixStack, queue: OrderedRenderCommandQueue) {
    matrices.push()
    matrices.translate(blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble())
    for (direction in Direction.entries) {
        queue.drawSide(texture, matrices, direction)
    }
    matrices.pop()
}

fun OrderedRenderCommandQueue.drawSide(layerTexture: DecalsLayerTexture, matrices: MatrixStack, side: Direction) {
    val (x0, y0) = side.getStartPos()
    val u0 = x0.toFloat() / 48f
    val v0 = y0.toFloat() / 32f
    val u1 = (x0 + 16).toFloat() / 48f
    val v1 = (y0 + 16).toFloat() / 32f
    val light = LightmapTextureManager.MAX_LIGHT_COORDINATE
    val normal = side.normal.let { Vector3f(it.x, it.y, it.z) }
    val epsilon = 0.001f

    submitCustom(matrices, RenderLayer.getEntityCutoutNoCull(layerTexture.id)) { entry, vc ->
        fun submitVertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
            vc
                .vertex(entry, x + (normal.x * epsilon), y + (normal.y * epsilon), z + + (normal.z * epsilon))
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .color(Colors.WHITE)
                .light(light)
                .normal(entry, normal)
        }

        when (side) {
            Direction.UP, Direction.DOWN -> {
                val y = if (side == Direction.UP) 1f else 0f
                submitVertex(0f, y, 0f, u0, v0)
                submitVertex(0f, y, 1f, u0, v1)
                submitVertex(1f, y, 1f, u1, v1)
                submitVertex(1f, y, 0f, u1, v0)
            }
            Direction.NORTH, Direction.SOUTH -> {
                val z = if (side == Direction.NORTH) 0f else 1f
                submitVertex(0f, 0f, z, u0, v0)
                submitVertex(0f, 1f, z, u0, v1)
                submitVertex(1f, 1f, z, u1, v1)
                submitVertex(1f, 0f, z, u1, v0)
            }
            Direction.EAST, Direction.WEST -> {
                val x = if (side == Direction.EAST) 1f else 0f
                submitVertex(x, 0f, 0f, u0, v0)
                submitVertex(x, 1f, 0f, u0, v1)
                submitVertex(x, 1f, 1f, u1, v1)
                submitVertex(x, 0f, 1f, u1, v0)
            }
        }
    }
}
