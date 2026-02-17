package org.lain.engine.client.mc.render

import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.command.OrderedRenderCommandQueue
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.client.texture.TextureManager
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Colors
import net.minecraft.util.math.ColorHelper
import org.joml.Vector3f
import org.lain.engine.util.EngineId
import org.lain.engine.util.flush
import org.lain.engine.util.math.Pos
import org.lain.engine.util.math.isPowerOfTwo
import org.lain.engine.util.math.squaredDistanceTo
import org.lain.engine.world.*
import kotlin.math.max
import kotlin.math.min

data class BlockDecalImageData(
    val gameTexture: DecalsTexture
)

class ChunkDecalsStorage {
    lateinit var textureManager: TextureManager
    private val images: MutableMap<EngineChunkPos, MutableMap<ImmutableVoxelPos, BlockDecalImageData>> = mutableMapOf()

    fun unloadTextures(chunkPos: EngineChunkPos) {
        images.remove(chunkPos)?.forEach { (pos, image) -> unloadTexture(pos, chunkPos) }
    }

    fun unloadTexture(voxelPos: VoxelPos, chunkPos: EngineChunkPos = EngineChunkPos(voxelPos)) {
        textures(chunkPos).remove(voxelPos)?.let {
            val texture = it.gameTexture
            textureManager.destroyTexture(texture.id)
            texture.close()
        }
    }

    fun loadTextures(pos: EngineChunkPos, chunk: EngineChunk) = chunk.decals.forEach { (voxelPos, decals) ->
        updateTexture(decals, ImmutableVoxelPos(voxelPos), pos)
    }

    fun updateTexture(decals: BlockDecals, voxelPos: ImmutableVoxelPos, chunk: EngineChunkPos = EngineChunkPos(voxelPos)) {
        var image = textures(chunk)[voxelPos]
        val decalLayers = decals.layers
        val maxResolution = decalLayers.maxOf { layer -> layer.key.resolution }

        if (image == null) {
            val texture = DecalsTexture(voxelPos, maxResolution)
            texture.register(textureManager)
            image = BlockDecalImageData(texture)
            textures(chunk)[voxelPos] = image
        }

        image.gameTexture.compile(decalLayers)
    }

    private fun textures(chunk: EngineChunkPos) = images.computeIfAbsent(chunk) { mutableMapOf() }

    fun unload() {
        images.toMap().forEach { unloadTextures(it.key) }
    }

    fun getBlockImages(basePos: Pos, renderDistanceChunks: Int): List<Pair<VoxelPos, BlockDecalImageData>> {
        return images
            .filter {
                squaredDistanceTo(
                    it.key.x,
                    it.key.z,
                    basePos.x.toInt() / 16,
                    basePos.z.toInt() / 16
                ) < renderDistanceChunks * renderDistanceChunks
            }
            .flatMap { it.value.toList() }
    }

    fun handleDecalsEvent(world: World) = world.events<DecalEvent>().flush { event ->
        val decals = world.chunkStorage.getDecals(event.pos) ?: run {
            unloadTexture(event.pos, event.chunk)
            return@flush
        }
        updateTexture(decals, ImmutableVoxelPos(event.pos), event.chunk)
    }
}

class DecalsTexture(val blockPos: VoxelPos, val resolution: Int) : AutoCloseable {
    // Полная развёртка всех сторон блока
    val width = 3 * resolution
    val height = 2 * resolution
    private val texture = NativeImageBackedTexture(
        "Block Decals ${blockPos.toShortString()}",
        width,
        height,
        true
    )
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

    fun compile(layers: Map<DecalsLayerType, DecalsLayer>) {
        if (layers.keys.any { !isPowerOfTwo(it.resolution) }) {
            error("Слои должны иметь разрешение в степенях двойки (16, 32, 64...)")
        }

        val image = texture.image ?: return
        image.fillRect(0, 0, width, height, 0)
        for ((type, layer) in layers) {
            val scale = resolution / type.resolution
            for ((direction, decals) in layer.directions) {
                val (x0, y0) = direction.getStartPos(resolution)
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

                    when (val contents = decal.contents) {
                        is DecalContents.Chip -> {
                            val radius = contents.radius
                            val halfRadius = radius / 2

                            repeat(radius) { i ->
                                repeat(radius) { j ->
                                    val px = decal.x - halfRadius + i
                                    val py = decal.y - halfRadius + j
                                    if (px in 0 until width && py in 0 until height) {
                                        image.setColorArgb(
                                            x0 + px,
                                            y0 + py,
                                            ColorHelper.withAlpha(contents.opacity, Colors.BLACK)
                                        )
                                        depthArea.stretch(px, py,)
                                    }
                                }
                            }
                        }
                    }

                    depthArea.apply {
                        x1 = (x1 - 1).coerceIn(0, type.resolution - 1)
                        y1 = (y1 - 1).coerceIn(0, type.resolution - 1)
                        x2 = (x2 + 1).coerceIn(0, type.resolution - 1)
                        y2 = (y2 + 1).coerceIn(0, type.resolution - 1)
                    }
                }
            }
        }
        texture.upload()
    }
}

private fun Direction.getStartPos(resolution: Int) = when(this) {
    Direction.DOWN -> 0 to 0
    Direction.UP -> 0 to resolution
    Direction.NORTH -> resolution to 0
    Direction.SOUTH -> resolution to resolution
    Direction.WEST -> resolution * 2 to 0
    Direction.EAST -> resolution * 2 to resolution
}

fun renderBlockDecals(texture: DecalsTexture, blockPos: VoxelPos, matrices: MatrixStack, queue: OrderedRenderCommandQueue) {
    matrices.push()
    matrices.translate(blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble())
    for (direction in Direction.entries) {
        queue.drawSide(texture, matrices, direction)
    }
    matrices.pop()
}

private fun OrderedRenderCommandQueue.drawSide(
    layerTexture: DecalsTexture,
    matrices: MatrixStack,
    side: Direction
) {
    val (x0, y0) = side.getStartPos(layerTexture.resolution)
    val light = LightmapTextureManager.MAX_LIGHT_COORDINATE
    val normal = side.normal.let { Vector3f(it.x, it.y, it.z) }
    val epsilon = 0.001f
    val depthMap = layerTexture.depths[side] ?: return

    val width = layerTexture.width.toFloat()
    val height = layerTexture.height.toFloat()
    val resolution = layerTexture.resolution.toFloat()

    depthMap.forEach { (depth, area) ->
        val u0 = (x0 + area.x1) / width
        val v0 = (y0 + area.y1) / height
        val u1 = (x0 + area.x2) / width
        val v1 = (y0 + area.y2) / height

        val px0 = area.x1 / resolution
        val py0 = area.y1 / resolution
        val px1 = area.x2 / resolution
        val py1 = area.y2 / resolution

        submitCustom(matrices, RenderLayer.getEntityTranslucent(layerTexture.id)) { entry, vc ->
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

