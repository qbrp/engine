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
import org.lain.engine.mc.setBlockDecals
import org.lain.engine.util.EngineId
import org.lain.engine.util.alsoForEach
import org.lain.engine.world.BlockDecals
import org.lain.engine.world.DecalContents
import org.lain.engine.world.DecalsLayer
import org.lain.engine.world.Direction
import kotlin.math.max
import kotlin.math.min

typealias ChunkBlockDecals = MutableMap<BlockPos, BlockDecals>

data class BlockDecalImageData(val layers: MutableList<DecalsLayerTexture>)

class ChunkDecalsStorage {
    lateinit var textureManager: TextureManager
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
        chunk.setBlockDecals(pos, decals)
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

    fun clear() {
        chunks.keys.toList().forEach { unload(it) }
    }

    fun update(chunk: Chunk, pos: BlockPos) {
        RenderSystem.assertOnRenderThread()
        val chunkPos = chunk.pos
        val blocks = (chunk as AttachmentTarget).getAttached(BLOCK_DECALS_ATTACHMENT_TYPE)?.toMap() ?: emptyMap()
        val oldBlocks = chunks[chunkPos] ?: emptyMap()
        val oldBlockDecals = oldBlocks[pos]
        val newBlockDecals = blocks[pos]
        update(
            chunkPos,
            mutableMapOf(pos to newBlockDecals),
            mapOf(pos to oldBlockDecals)
        )
    }

    private fun update(chunkPos: ChunkPos, newBlocks: Map<BlockPos, BlockDecals?>, oldBlocks: Map<BlockPos, BlockDecals?>) {
        val remaining = oldBlocks.toMutableMap()
        for ((pos, newBlockDecals) in newBlocks) {
            if (newBlockDecals == null) continue
            val oldBlockDecals = oldBlocks[pos]
            if (oldBlockDecals?.version != newBlockDecals.version) {
                remaining.remove(pos)
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

                chunks.computeIfAbsent(chunkPos) { mutableMapOf() }[pos] = newBlockDecals
            }
        }

        for ((blockPos, decals) in remaining) {
            images.remove(ChunkPos(blockPos) to blockPos)
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
    val depths = mutableMapOf<Direction, MutableMap<Float, Area>>()

    data class Area(var x1: Int, var y1: Int, var x2: Int, var y2: Int) {
        fun stretch(x: Int, y: Int) {
            x2 = max(x, x2)
            x1 = min(x, x1)
            y2 = max(y, y2)
            y1 = min(y, y1)
        }
    }

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

            val depthAreas = depths.getOrPut(direction) { mutableMapOf() }

            for (decal in decals) {
                val depthArea = depthAreas.getOrPut(decal.depth) {
                    Area(
                        Int.MAX_VALUE,
                        Int.MAX_VALUE,
                        Int.MIN_VALUE,
                        Int.MIN_VALUE
                    )
                }

                when(val contents = decal.contents) {
                    is DecalContents.Chip -> {
                        val radius = contents.radius
                        val halfRadius = radius / 2
                        val (pX0, pY0) = x0 - halfRadius to y0 - halfRadius

                        repeat(radius) { i ->
                            repeat(radius) { j ->
                                val px = pX0 + decal.x - halfRadius + i
                                val py = pY0 + decal.y - halfRadius + j
                                if (px in 0 until 48 && py in 0 until 32) {
                                    image.setColorArgb(px, py, Colors.BLACK)
                                    depthArea.stretch(
                                        px - x0,
                                        py - y0,
                                    )
                                }
                            }
                        }
                    }
                }

                depthArea.apply {
                    x1 = (x1 - 1).coerceIn(0, 16)
                    y1 = (y1 - 1).coerceIn(0, 16)
                    x2 = (x2 + 1).coerceIn(0, 16)
                    y2 = (y2 + 1).coerceIn(0, 16)
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

private fun OrderedRenderCommandQueue.drawSide(
    layerTexture: DecalsLayerTexture,
    matrices: MatrixStack,
    side: Direction
) {
    val (x0, y0) = side.getStartPos()
    val light = LightmapTextureManager.MAX_LIGHT_COORDINATE
    val normal = side.normal.let { Vector3f(it.x, it.y, it.z) }
    val epsilon = 0.001f
    val depthMap = layerTexture.depths[side] ?: return

    depthMap.forEach { depth, area ->

        // UV
        val u0 = (x0 + area.x1) / 48f
        val v0 = (y0 + area.y1) / 32f
        val u1 = (x0 + area.x2) / 48f
        val v1 = (y0 + area.y2) / 32f

        // ЛОКАЛЬНЫЕ координаты на стороне (0..1)
        val px0 = area.x1 / 16f
        val py0 = area.y1 / 16f
        val px1 = area.x2 / 16f
        val py1 = area.y2 / 16f

        submitCustom(matrices, RenderLayer.getEntityCutoutNoCull(layerTexture.id)) { entry, vc ->
            fun submitVertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
                vc.vertex(
                    entry,
                    x + normal.x * (epsilon - depth),
                    y + normal.y * (epsilon - depth),
                    z + normal.z * (epsilon - depth)
                )
                    .texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .color(Colors.WHITE)
                    .light(light)
                    .normal(entry, normal)
            }

            when (side) {
                Direction.UP, Direction.DOWN -> {
                    val y = if (side == Direction.UP) 1f else 0f

                    submitVertex(px0, y, py0, u0, v0)
                    submitVertex(px0, y, py1, u0, v1)
                    submitVertex(px1, y, py1, u1, v1)
                    submitVertex(px1, y, py0, u1, v0)
                }

                Direction.NORTH, Direction.SOUTH -> {
                    val z = if (side == Direction.NORTH) 0f else 1f

                    submitVertex(px0, py0, z, u0, v0)
                    submitVertex(px0, py1, z, u0, v1)
                    submitVertex(px1, py1, z, u1, v1)
                    submitVertex(px1, py0, z, u1, v0)
                }

                Direction.EAST, Direction.WEST -> {
                    val x = if (side == Direction.EAST) 1f else 0f

                    submitVertex(x, py0, px0, u0, v0)
                    submitVertex(x, py1, px0, u0, v1)
                    submitVertex(x, py1, px1, u1, v1)
                    submitVertex(x, py0, px1, u1, v0)
                }
            }
        }
    }
}

