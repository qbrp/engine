@file:OptIn(ExperimentalSerializationApi::class)
package org.lain.engine.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.lain.engine.server.EngineServer
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

fun saveChunk(
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

fun loadChunk(server: EngineServer, pos: EngineChunkPos): ChunkPersistent? {
    val file = server.chunkRegionPath(pos)
    return if (file.exists()) {
        CborSerializer.decodeFromByteArray<ChunkPersistent>(file.readBytes())
    } else {
        null
    }
}