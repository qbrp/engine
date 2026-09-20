@file:OptIn(ExperimentalSerializationApi::class)

package org.lain.engine.data

import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.cyberia.ecs.destroy
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.server.EngineServer
import org.lain.engine.util.LogDiagnosticContext
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.world.*
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class ChunkPersistent(
    val decals: Map<ImmutableVoxelPos, BlockDecals> = mapOf(),
    val hints: Map<ImmutableVoxelPos, Hint> = mapOf(),
    val voxels: Map<ImmutableVoxelPos, PersistentId> = mapOf(),
)

private data class ChunkEntitySnapshot(
    val uuid: PersistentId,
    val components: List<SavingComponentSnapshot>,
)

class ChunkPersistenceException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

private val EngineServer.chunkRegionsPath
    get() = globals.savePath.resolve("engine-regions")

fun EngineServer.chunkRegionPath(world: WorldId, chunkPos: EngineChunkPos): File {
    val worldDirectory = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(world.value.toByteArray(Charsets.UTF_8))
    val regionX = chunkPos.regionX()
    val regionZ = chunkPos.regionZ()
    return chunkRegionsPath.resolve(
        "$worldDirectory/r.$regionX.$regionZ/c.${chunkPos.x}.${chunkPos.z}.bin"
    )
}

class ChunkPersistence(
    private val server: EngineServer,
    private val database: Database,
    dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4),
) {
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + dispatcher)
    private val operationTails = ConcurrentHashMap<Path, Job>()
    private val failedSaves = ConcurrentHashMap<Path, ChunkPersistenceException>()
    private val pendingLoads = mutableMapOf<Path, PendingChunkLoad>()

    @Volatile
    private var closed = false

    fun saveChunk(world: World, pos: EngineChunkPos, chunk: EngineChunk) {
        check(server.isOnThread())
        check(!closed)

        val file = server.chunkRegionPath(world.id, pos)
        val snapshot = createSaveSnapshot(world, chunk)

        world.chunkStorage.removeChunk(pos)
        snapshot.entitiesToUnload.forEach { (entity, _) ->
            with(world) { entity.destroy() }
        }

        val job = enqueue(file.toPath()) {
            persistSnapshot(file, world.id, pos, snapshot)
        }
        job.invokeOnCompletion { cause ->
            if (cause != null) {
                LOGGER.error("Не удалось сохранить чанк {} в мире {}", pos, world.id, cause)
            }
        }
    }

    fun loadChunk(world: World, pos: EngineChunkPos): EngineChunk {
        check(server.isOnThread())
        world.chunkStorage.getChunk(pos)?.let { return it }

        val pending = beginChunkLoad(world, pos)
        val prepared = runCatching {
            runBlocking { pending.deferred.await() }
        }
        return finishChunkLoad(world, pos, pending, prepared)
    }

    fun loadChunkAsync(world: World, pos: EngineChunkPos) {
        check(server.isOnThread())
        if (world.chunkStorage.getChunk(pos) != null) return

        beginChunkLoad(world, pos)
    }

    private fun beginChunkLoad(world: World, pos: EngineChunkPos): PendingChunkLoad {
        val file = server.chunkRegionPath(world.id, pos)
        val path = file.toPath()
        pendingLoads[path]?.let { return it }

        val pending = PendingChunkLoad(
            path,
            enqueue(path) {
                throwPreviousFailure(path)
                prepareChunkLoad(world, file)
            },
        )
        pendingLoads[path] = pending
        scope.launch {
            val prepared = runCatching { pending.deferred.await() }
            server.execute {
                if (pendingLoads[pending.path] === pending) {
                    runCatching {
                        finishChunkLoad(world, pos, pending, prepared)
                    }.onFailure { cause ->
                        LOGGER.error("Failed to load engine chunk {} in world {}", pos, world.id, cause)
                    }
                }
            }
        }
        return pending
    }

    private fun finishChunkLoad(
        world: World,
        pos: EngineChunkPos,
        pending: PendingChunkLoad,
        preparedResult: Result<PreparedChunkLoad?>,
    ): EngineChunk {
        check(server.isOnThread())
        if (!pendingLoads.remove(pending.path, pending)) {
            world.chunkStorage.getChunk(pos)?.let { return it }
            val cause = preparedResult.exceptionOrNull()
                ?: ChunkPersistenceException(
                    "Engine chunk $pos in world ${world.id} finished loading without a result"
                )
            throw cause.asChunkLoadException(world, pos)
        }

        return try {
            val prepared = preparedResult.getOrThrow()
            check(world.chunkStorage.getChunk(pos) == null) {
                "Chunk $pos in world ${world.id} was loaded outside ChunkPersistence"
            }
            prepared?.transaction?.commit()
            (prepared?.chunk ?: EngineChunk()).also { chunk ->
                world.chunkStorage.setChunk(pos, chunk)
            }
        } catch (cause: Throwable) {
            throw cause.asChunkLoadException(world, pos)
        }
    }

    suspend fun close() {
        check(server.isOnThread())
        closed = true

        withContext(NonCancellable) {
            operationTails.values.toList().joinAll()
            supervisor.complete()
            supervisor.join()
        }

        val failures = failedSaves.values.toList()
        if (failures.isNotEmpty()) {
            val exception = ChunkPersistenceException(
                "Failed to persist ${failures.size} engine chunk(s) during shutdown"
            )
            failures.forEach(exception::addSuppressed)
            throw exception
        }
    }

    private fun createSaveSnapshot(world: World, chunk: EngineChunk): ChunkSaveSnapshot {
        val entities = mutableListOf<ChunkEntitySnapshot>()
        val entitiesToUnload = mutableListOf<Pair<EntityId, EntityUnload>>()
        val voxelReferences = mutableMapOf<ImmutableVoxelPos, PersistentId>()

        chunk.dynamicVoxels.forEach { (voxelPos, entity) ->
            val persistentId = with(world) {
                entity.requireComponent<PersistentIdComponent>().id
            }
            val components = with(world) { entity.savingSnapshot() }

            entities += ChunkEntitySnapshot(
                persistentId,
                components,
            )
            voxelReferences[voxelPos] = persistentId

            server.entityCoordinator.tryUnloadEntity(world, persistentId)?.let { unload ->
                entitiesToUnload += entity to unload
            }
        }

        return ChunkSaveSnapshot(
            ChunkPersistent(
                chunk.decals.toMap(),
                chunk.hints.toMap(),
                voxelReferences,
            ),
            entities,
            entitiesToUnload,
        )
    }

    private suspend fun prepareChunkLoad(world: World, file: File): PreparedChunkLoad? {
        val chunk = readChunk(file) ?: return null
        val transaction = server.createTransactionContext(world)

        return try {
            val voxels = linkedMapOf<ImmutableVoxelPos, EntityId>()
            chunk.voxels.forEach { (voxelPos, persistentId) ->
                val entity = transaction.withChild { child ->
                    context(LogDiagnosticContext()) {
                        EntityLoadOperation(persistentId, database, child)
                            .execute()
                            .getEntityOrThrow()
                    }
                }
                context(server.luaScriptEngine, transaction.commands) {
                    entity.setDynamicVoxel(voxelPos, persistentId, true)
                }
                voxels[voxelPos] = entity
            }

            PreparedChunkLoad(
                transaction,
                EngineChunk(
                    chunk.decals.toMutableMap(),
                    chunk.hints.toMutableMap(),
                    voxels,
                ),
            )
        } catch (cause: Throwable) {
            transaction.rollback(cause)
            throw cause
        }
    }

    private fun readChunk(file: File): ChunkPersistent? {
        return if (file.exists()) {
            Cbor.decodeFromByteArray(file.readBytes())
        } else {
            null
        }
    }

    private fun throwPreviousFailure(path: Path) {
        failedSaves[path]?.let { throw it }
    }

    private suspend fun persistSnapshot(
        file: File,
        world: WorldId,
        pos: EngineChunkPos,
        snapshot: ChunkSaveSnapshot,
    ) {
        val path = file.toPath()
        try {
            database.saveChunkEntities(snapshot.entities)
            file.writeBytesAtomically(Cbor.encodeToByteArray(snapshot.chunk))
        } catch (cause: Throwable) {
            val exception = cause as? ChunkPersistenceException
                ?: ChunkPersistenceException(
                    "Failed to persist engine chunk $pos in world $world",
                    cause,
                )
            failedSaves[path] = exception
            throw exception
        }

        failedSaves.remove(path)
        snapshot.entitiesToUnload.forEach { (_, unload) ->
            server.entityCoordinator.finishEntityUnload(unload)
        }
    }

    private fun <T> enqueue(path: Path, operation: suspend () -> T): Deferred<T> {
        check(server.isOnThread()) { "Chunk operations must be enqueued on the server thread" }
        check(!closed) { "Chunk persistence is closed" }

        @Suppress("UNCHECKED_CAST")
        val deferred = operationTails.compute(path) { _, previous ->
            scope.async(start = CoroutineStart.LAZY) {
                previous?.join()
                operation()
            }
        } as Deferred<T>

        deferred.invokeOnCompletion {
            operationTails.remove(path, deferred)
        }
        deferred.start()
        return deferred
    }

    private data class ChunkSaveSnapshot(
        val chunk: ChunkPersistent,
        val entities: List<ChunkEntitySnapshot>,
        val entitiesToUnload: List<Pair<EntityId, EntityUnload>>,
    )

    private data class PreparedChunkLoad(
        val transaction: TransactionContext,
        val chunk: EngineChunk,
    )

    private class PendingChunkLoad(
        val path: Path,
        val deferred: Deferred<PreparedChunkLoad?>,
    )

    private companion object {
        val LOGGER = LoggerFactory.getLogger(ChunkPersistence::class.java)
    }
}

private fun Throwable.asChunkLoadException(
    world: World,
    pos: EngineChunkPos,
): ChunkPersistenceException {
    return this as? ChunkPersistenceException
        ?: ChunkPersistenceException(
            "Failed to load engine chunk $pos in world ${world.id}",
            this,
        )
}

private suspend fun Database.saveChunkEntities(entities: List<ChunkEntitySnapshot>) {
    saveEntitiesBatch(
        entities = entities.map { EntityBatchDto(it.uuid, EntityDatabaseKind.VOXEL) },
        data = EntityPersistenceDataBatchDto(emptyList()),
        components = entities.flatMap { entity ->
            entity.components.map { component ->
                ComponentBatchDto(entity.uuid, component.serializeToRecord())
            }
        },
        ownerships = emptyList(),
    )
}

private fun File.writeBytesAtomically(bytes: ByteArray) {
    val target = toPath()
    val directory = target.parent
    Files.createDirectories(directory)
    val temp = Files.createTempFile(directory, "$name.", ".tmp")

    try {
        FileChannel.open(temp, StandardOpenOption.WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
            channel.force(true)
        }

        try {
            Files.move(
                temp,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temp)
    }
}
