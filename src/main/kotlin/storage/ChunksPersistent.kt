@file:OptIn(ExperimentalSerializationApi::class)
package org.lain.engine.storage

import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.engine.server.EngineServer
import org.lain.engine.util.component.EntityCommandBuffer
import org.lain.engine.util.file.ensureExists
import org.lain.engine.world.*
import java.io.File

private val ChunkIoCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val CborSerializer = Cbor {
    serializersModule = SerializersModule {
        polymorphic(VoxelPos::class) {
            subclass(MutableVoxelPos::class)
            subclass(ImmutableVoxelPos::class)
        }
        polymorphicComponentSubclasses()
    }
}

@Serializable
data class ChunkPersistent(
    val decals: Map<VoxelPos, BlockDecals> = mapOf(),
    val hints: Map<VoxelPos, BlockHint> = mapOf(),
    val voxels: Map<VoxelPos, List<ComponentDto>> = mapOf()
)

private val EngineServer.chunkRegionsPath
    get() = globals.savePath
        .resolve("engine-regions")
        .also { it.mkdir() }

fun EngineServer.chunkRegionPath(chunkPos: EngineChunkPos): File {
    val regionX = chunkPos.regionX()
    val regionZ = chunkPos.regionZ()
    val x = chunkPos.x
    val z = chunkPos.z
    return chunkRegionsPath.resolve("r.$regionX.$regionZ/c.$x.$z.bin")
}

fun saveChunkAsync(
    server: EngineServer,
    pos: EngineChunkPos,
    decals: Map<VoxelPos, BlockDecals>,
    hints: Map<VoxelPos, BlockHint>,
    voxels: Map<VoxelPos, List<ComponentDto>>
) {
    ChunkIoCoroutineScope.launch {
        val file = server.chunkRegionPath(pos)
        file.ensureExists()
        file.writeBytes(
            CborSerializer.encodeToByteArray(
                ChunkPersistent(decals, hints, voxels)
            )
        )
    }
}
class ChunkLoader(
    private val server: EngineServer,
    private val database: Database,
) {
    fun loadChunk(world: World, pos: EngineChunkPos): EngineChunk? = runBlocking {
        val chunkPersistent = loadChunkPersistent(pos) ?: return@runBlocking null
        val entityResolver = DatabaseEntityResolver(database)
        val ecb = EntityCommandBuffer(world)
        val voxelJobs = chunkPersistent.voxels.map { (voxelPos, components) ->
            async(Dispatchers.IO) {
                with(ecb) {
                    val entity = entityResolver.loadEntity(
                        world.componentLoadSettings,
                        components
                    )

                    entity.setDynamicVoxel(voxelPos, true)
                    voxelPos to entity
                }
            }
        }

        val voxels = voxelJobs.awaitAll()
            .associate { it.first to it.second }
            .toMutableMap()

        ecb.apply(world)

        EngineChunk(
            chunkPersistent.decals.toMutableMap(),
            chunkPersistent.hints.toMutableMap(),
            voxels
        )
    }

    private fun loadChunkPersistent(pos: EngineChunkPos): ChunkPersistent? {
        val file = server.chunkRegionPath(pos)
        return if (file.exists()) {
            CborSerializer.decodeFromByteArray(file.readBytes())
        } else {
            null
        }
    }
}