package org.lain.engine.mc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import net.minecraft.world.chunk.Chunk
import org.lain.engine.chat.acoustic.AcousticGeneration
import org.lain.engine.chat.acoustic.AcousticSimulationResult
import org.lain.engine.chat.acoustic.AcousticSimulator
import org.lain.engine.chat.acoustic.Grid3Range
import org.lain.engine.chat.acoustic.Grid3f
import org.lain.engine.chat.acoustic.NEIGHBOURS_VON_NEUMANN
import org.lain.engine.chat.acoustic.SceneSize
import org.lain.engine.chat.acoustic.freeGrid3f
import org.lain.engine.chat.acoustic.getGrid3f
import org.lain.engine.chat.acoustic.simulateAsync
import org.lain.engine.util.Pos
import org.lain.engine.util.PrimitiveArrayPool
import org.lain.engine.util.Timestamp
import org.lain.engine.util.isPowerOfTwo
import org.lain.engine.util.minecraftChunkSectionCoord
import org.lain.engine.world.WorldId
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.set
import kotlin.jvm.optionals.getOrNull
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class InvalidMessageSourcePositionException(val y: Int) : RuntimeException("Message source is too high or low")

const val SEGMENT_SIZE = 64f

fun World.segmentOf(y: Int) = floor((y - bottomY) / SEGMENT_SIZE).toInt()

val World.segmentCount get() = ceil(height / SEGMENT_SIZE).toInt()

data class AcousticBlockData(
    val solid: Float,
    val air: Float,
    val partial: Float,
    val blocks: Map<Identifier, Float>,
    val tags: Map<TagKey<Block>, Float>,
) {
    fun getPassability(pos: BlockPos, world: World, blockstate: BlockState): Float {
        val blockRegistryKey = blockstate.registryEntry.key.getOrNull()

        val tag = tags.maxOfOrNull { (tag, volume) ->
            if (blockstate.isIn(tag)) volume else 0f
        } ?: 0f

        val isFullCube = blockstate.isFullCube(world, pos)
        val isSolid = blockstate.isSolid

        val default = if (isSolid) {
            if (isFullCube) {
                solid
            } else {
                partial
            }
        } else {
            air
        }

        val blockId = blockRegistryKey?.value

        return blocks[blockId] ?: max(tag, default)
    }

    companion object {
        val BUILTIN = AcousticBlockData(0.0f, 0.95f, 0.9f, mapOf(), mapOf())
    }
}

class MinecraftChunkAcousticScene private constructor(
    val passability: Grid3f,
    val size: SceneSize,
    val x: Int,
    val y: Int,
    val z: Int,
    val chunk: Chunk
) {
    private val actors = AtomicInteger(0)
    private val shouldDestroy = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)

    fun copy(): MinecraftChunkAcousticScene {
        val newPassabilityGrid = PrimitiveArrayPool.getGrid3f(size.width, size.height, size.depth)
        passability.arraycopy(newPassabilityGrid)
        return MinecraftChunkAcousticScene(
            newPassabilityGrid,
            size,
            x, y, z,
            chunk
        )
    }

    fun setPassability(x: Int, y: Int, z: Int, value: Float) {
        assertVacant()
        passability[x - this.x, y - this.y, z - this.z] = value
    }

    fun getPassability(x: Int, y: Int, z: Int) = passability[x - this.x, y - this.y, z - this.z]

    fun occupy() {
        actors.incrementAndGet()
    }

    fun vacant() {
        if (actors.decrementAndGet() == 0 && shouldDestroy.get()) {
            _destroy()
        }
    }

    fun destroy() {
        shouldDestroy.set(true)
        if (actors.get() == 0) {
            _destroy()
        }
    }

    private fun _destroy() {
        if (destroyed.get()) return
        PrimitiveArrayPool.freeGrid3f(passability)
        destroyed.set(true)
    }

    private fun assertVacant() {
        if (actors.get() > 0) throw IllegalStateException("Сцена не может быть изменена во время использования")
    }

    companion object {
        fun create(
            world: World,
            chunk: Chunk,
            acousticBlockData: AcousticBlockData,
            x0: Int = 0, x1: Int = 16,
            y0: Int, y1: Int,
            z0: Int = 0, z1: Int = 16
        ): MinecraftChunkAcousticScene {
            val pos = BlockPos.Mutable()
            val startX = chunk.pos.startX
            val startZ = chunk.pos.startZ
            val startY = y0
            val height = chunk.height
            val minY = chunk.bottomY

            val sceneWidth = x1 - x0
            val sceneHeight = y1 - y0
            val sceneDepth = z1 - z0
            val size = SceneSize(sceneWidth, sceneHeight, sceneDepth)

            require(y0 >= minY); require(y1 <= height)
            require(x0 >= 0); require(x1 <= 16)
            require(z0 >= 0); require(z1 <= 16)

            val passabilityGrid = PrimitiveArrayPool.getGrid3f(sceneWidth, sceneHeight, sceneDepth)

            passabilityGrid.map { idx, lx, ly, lz ->
                pos.x = startX + lx
                pos.y = startY + ly
                pos.z = startZ + lz
                acousticBlockData.getPassability(pos, world, chunk.getBlockState(pos))
            }

            val offsetX = startX + x0
            val offsetY = y0
            val offsetZ = startZ + z0

            return MinecraftChunkAcousticScene(passabilityGrid, size, offsetX, offsetY, offsetZ, chunk)
        }
    }
}

/**
 * Абстрактное пространство из игровых сцен одинакового размера.
 * Предоставляет доступ к ним через единую локальную систему координат от самой первой сцены.
 * Для получения локальных координат из мировых использовать `worldToLocal`
 */
class ChunkedAcousticView(
    val chunkSize: ChunkSize,
    private val scenes: List<MinecraftChunkAcousticScene>
) {
    data class ChunkSize(val w: Int, val h: Int, val d: Int) {
        init {
            require(isPowerOfTwo(w))
            require(isPowerOfTwo(h))
            require(isPowerOfTwo(d))
        }
        val widthTrailingZeros = Integer.numberOfTrailingZeros(w)
        val heightTrailingZeros = Integer.numberOfTrailingZeros(h)
        val depthTrailingZeros = Integer.numberOfTrailingZeros(d)

        fun chunkX(x: Int) = x shr widthTrailingZeros
        fun chunkY(y: Int) = y shr heightTrailingZeros
        fun chunkZ(z: Int) = z shr depthTrailingZeros
    }

    data class ChunkPos(var x: Int, var y: Int, var z: Int)

    val minX = scenes.minOf { it.x }
    val minY = scenes.minOf { it.y }
    val minZ = scenes.minOf { it.z }

    val maxX = scenes.maxOf { it.x + it.size.width }
    val maxY = scenes.maxOf { it.y + it.size.height }
    val maxZ = scenes.maxOf { it.z + it.size.depth }

    val viewW = maxX - minX
    val viewH = maxY - minY
    val viewD = maxZ - minZ
    val totalSize = SceneSize(viewW, viewH, viewD)

    val chunkMap = ConcurrentHashMap<ChunkPos, MinecraftChunkAcousticScene>()

    init {
        scenes.forEach {
            it.occupy()
            chunkMap[
                ChunkPos(
                    chunkSize.chunkX(it.x - minX),
                    chunkSize.chunkY(it.y - minY),
                    chunkSize.chunkZ(it.z - minZ)
                )
            ] = it
        }
    }

    fun worldToLocal(x: Int, y: Int, z: Int): Triple<Int, Int, Int> =
        Triple(x - minX, y - minY, z - minZ)

    fun free() {
        scenes.forEach { it.vacant() }
    }

    inline fun forEachCell(
        range: Grid3Range,
        block: (idx: Int, passability: Float, x: Int, y: Int, z: Int) -> Unit
    ) {
        var idx = 0
        val chunkPos = ChunkPos(0, 0, 0)

        for (x in range.x0 until range.x1) {
            chunkPos.x = chunkSize.chunkX(x)

            for (y in range.y0 until range.y1) {
                chunkPos.y = chunkSize.chunkY(y)

                for (z in range.z0 until range.z1) {
                    try {
                        chunkPos.z = chunkSize.chunkZ(z)
                        val chunk = chunkMap[chunkPos] ?: error("Координаты выходят за пределы чанка")

                        val baseX = chunk.x - minX
                        val baseY = chunk.y - minY
                        val baseZ = chunk.z - minZ

                        val pass = chunk.passability[
                            x - baseX,
                            y - baseY,
                            z - baseZ
                        ]

                        block(idx++, pass, x, y, z)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        throw e
                    }
                }
            }
        }
    }
}

class ConcurrentAcousticSceneBank {
    // Упакованные сцены высотой в 64 блока
    data class AcousticSceneSegmentCompound(
        val scenes: MutableList<MinecraftChunkAcousticScene> = Collections.synchronizedList(mutableListOf())
    ) {
        fun getScene(segment: Int): MinecraftChunkAcousticScene {
            return scenes[segment]
        }
    }

    private data class WorldChunkKey(val world: WorldId, val pos: ChunkPos)

    private val chunkMap = ConcurrentHashMap<WorldChunkKey, AcousticSceneSegmentCompound>()

    fun addChunk(world: World, chunk: Chunk, acousticBlockData: AcousticBlockData): AcousticSceneSegmentCompound {
        val topY = chunk.topYInclusive
        val bottomY = chunk.bottomY
        val segments = world.segmentCount
        val segmentSize = SEGMENT_SIZE.toInt()

        val scenes = mutableListOf<MinecraftChunkAcousticScene>()

        for (segmentIndex in 0..segments) {
            val y0 = bottomY + segmentIndex * segmentSize
            val y1 = min(y0 + segmentSize, topY)

            if (y0 >= topY || y1 >= topY) {
                break
            }

            scenes.add(MinecraftChunkAcousticScene.create(world, chunk, acousticBlockData, y0 = y0, y1 = y1))
        }

        val worldId = world.engine
        val key = WorldChunkKey(worldId, chunk.pos)
        val compound = AcousticSceneSegmentCompound(scenes.toMutableList())
        removeChunk(worldId, chunk.pos)
        chunkMap[key] = compound
        return compound
    }

    fun removeChunk(world: WorldId, chunk: ChunkPos) {
        chunkMap.remove(WorldChunkKey(world, chunk))
            ?.also {
                it.scenes.forEach { scene ->
                    scene.destroy()
                }
            }
    }

    fun setPassability(world: World, pos: BlockPos, value: Float): MinecraftChunkAcousticScene? {
        val chunkPos = ChunkPos(pos)
        val oldSegment = getChunkSegment(world, chunkPos, pos.y) ?: return null

        if (oldSegment.getPassability(pos.x, pos.y, pos.z) == value) return null

        synchronized(oldSegment) {
             val newSegment = oldSegment.copy()
            oldSegment.destroy()
            newSegment.setPassability(pos.x, pos.y, pos.z, value)

            val list = chunkMap[WorldChunkKey(world.engine, chunkPos)]?.scenes ?: return null
            list[world.segmentOf(pos.y)] = newSegment

            return newSegment
        }
    }

    fun getChunkedView(
        world: World,
        acousticBlockData: AcousticBlockData,
        x0: Int, z0: Int,
        x1: Int, z1: Int,
        y: Int
    ): ChunkedAcousticView {
        require(x0 < x1)
        val chunkX0 = minecraftChunkSectionCoord(x0)
        val chunkX1 = minecraftChunkSectionCoord(x1)
        require(z0 < z1)
        val chunkZ0 = minecraftChunkSectionCoord(z0)
        val chunkZ1 = minecraftChunkSectionCoord(z1)

        val segment = world.segmentOf(y)
        val chunks = mutableListOf<MinecraftChunkAcousticScene>()
        for (x in chunkX0..chunkX1) {
            for (z in chunkZ0..chunkZ1) {
                chunks.add(
                    getChunkSegment(
                        world,
                        ChunkPos(x, z),
                        y
                    ) ?: addChunk(
                        world,
                        world.getChunk(x, z),
                        acousticBlockData
                    ).getScene(segment)
                )
            }
        }
        return ChunkedAcousticView(
            ChunkedAcousticView.ChunkSize(16, 64, 16),
            chunks
        )
    }

    fun getChunkSegment(world: World, pos: ChunkPos, y: Int) = getChunk(world.engine, pos)?.getScene(world.segmentOf(y))

    private fun getChunk(world: WorldId, pos: ChunkPos) = chunkMap[WorldChunkKey(world, pos)]
}



class MinecraftAcousticManager(
    private val entityTable: EntityTable,
    private val acousticSceneBank: ConcurrentAcousticSceneBank,
    acousticBlockData: AcousticBlockData,
) : AcousticSimulator {
    private val logger = LoggerFactory.getLogger("Engine Acoustic Simulation")
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val threads = Runtime.getRuntime().availableProcessors()
    private val executors = Executors.newFixedThreadPool(threads)
    var acousticBlockData = AtomicReference(acousticBlockData)
    var range = AtomicInteger(32)
    var chunkSize = AtomicInteger(32)
    var steps = AtomicInteger(100)
    var performanceDebug = AtomicBoolean(false)

    fun updateBlock(block: BlockState, pos: BlockPos, world: World) {
        setPassabilityAt(world, pos, acousticBlockData.get().getPassability(pos, world, block))
    }

    fun removeBlock(pos: BlockPos, world: World) {
        setPassabilityAt(world, pos, acousticBlockData.get().air)
    }

    fun setPassabilityAt(world: World, pos: BlockPos, value: Float) = coroutineScope.launch {
        try {
            acousticSceneBank.setPassability(world, pos, value)
        } catch (e: Throwable) {
            val (x, y, z) = Triple(pos.x, pos.y, pos.z)
            logger.error("При изменении акустической сцены ($world $x $y $z на $value) возникла непредвиденная ошибка. Сцена перезаписывается.", e)
            acousticSceneBank.addChunk(world, world.getChunk(pos), acousticBlockData.get())
        }
    }

    fun onChunkUnload(world: WorldId, chunk: Chunk) = coroutineScope.launch {
        acousticSceneBank.removeChunk(world, chunk.pos)
    }

    override suspend fun simulateSingleSource(
        world: WorldId,
        pos: Pos,
        volume: Float,
        maxVolume: Float,
        multiplier: Float
    ): AcousticSimulationResult {
        val timestamp = Timestamp()
        val mcWorld = entityTable.getMcWorld(world) ?: throw IllegalArgumentException("World $world")
        val acousticBlockData = acousticBlockData.get()

        val y = pos.y.toInt()
        if (y > mcWorld.height || y < mcWorld.bottomY) {
            throw InvalidMessageSourcePositionException(pos.y.toInt())
        }
        val x = pos.x.toInt()
        val z = pos.z.toInt()

        val range = range.get()
        val x0 = x - range
        val z0 = z - range
        val x1 = x + range
        val z1 = z + range
        val scene = acousticSceneBank.getChunkedView(mcWorld, acousticBlockData, x0, z0, x1, z1, y)
        val size = scene.totalSize
        val generation = AcousticGeneration(
            PrimitiveArrayPool.getGrid3f(
                size.width,
                size.height,
                size.depth
            )
        )

        fun _finish() {
            PrimitiveArrayPool.freeGrid3f(generation.volume)
            scene.free()
        }

        val performanceDebug = performanceDebug.get()
        val steps = steps.get()
        val chunkSize = chunkSize.get()

        try {
            for (offset in NEIGHBOURS_VON_NEUMANN + intArrayOf(0, 0, 0)) {
                val blockPos = BlockPos(x, y, z)
                val passability = acousticBlockData.getPassability(blockPos, mcWorld, mcWorld.getBlockState(blockPos))
                val (lX, lY, lZ) = scene.worldToLocal(x + offset[0], y + offset[1], z + offset[2])
                generation.volume[lX, lY, lZ] = volume * passability
            }
            simulateAsync(
                scene,
                generation,
                executors,
                threads,
                steps,
                logger,
                performanceDebug,
                maxVolume,
                multiplier,
                chunkSize,
            )
            if (performanceDebug) logger.info(
                "[DEUBG] Просимулирована акустика в мире {} позиции {}, время обработки {} мс.",
                world,
                pos,
                timestamp.timeElapsed()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            _finish()
        }

        return object : AcousticSimulationResult {
            override fun getVolume(pos: Pos): Float? {
                val x = pos.x.toInt()
                val y = pos.y.toInt()
                val z = pos.z.toInt()
                if (x !in scene.minX..scene.maxX || y !in scene.minY..scene.maxY || z !in scene.minZ..scene.maxZ) {
                    return null
                }
                val (lx, ly, lz) = scene.worldToLocal(x, y, z)
                return generation.volume[lx, ly, lz]
            }

            override fun finish() {
                _finish()
            }
        }
    }
}