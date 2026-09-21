package org.lain.engine.test

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.lain.cyberia.ecs.componentTypeOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lain.engine.chat.acoustic.AcousticSimulator
import org.lain.engine.mc.literalText
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.player.*
import org.lain.engine.script.FileScriptSource
import org.lain.engine.script.ModuleManager
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.script.lua.LuaDataStorage
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerId
import org.lain.engine.server.ServerPlatform
import org.lain.engine.data.EntityDatabaseKind
import org.lain.engine.data.EntityPersistenceData
import org.lain.engine.data.EntityPersistentRecord
import org.lain.engine.data.SaveTimers
import org.lain.engine.data.SavingComponentSnapshot
import org.lain.engine.data.Uuid
import org.lain.engine.data.connectDatabase
import org.lain.engine.data.saveEntity
import org.lain.engine.data.snapshot
import org.lain.engine.data.stringRepresentation
import org.lain.engine.item.Item
import org.lain.engine.item.toItemPrefabId
import org.lain.engine.script.EngineId
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.util.Injector
import org.lain.engine.world.World
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.WorldId
import java.nio.file.Path
import java.util.UUID

class ServerPlayerTest : EngineTest() {
    @field:TempDir
    lateinit var tempDir: Path

    private lateinit var server: EngineServer
    private lateinit var transportContext: TestServerTransportContext

    @BeforeEach
    fun setup() {
        val namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())
        val scriptsPath = tempDir.resolve("scripts").toFile()
        scriptsPath.mkdirs()
        val entrypoint = scriptsPath.resolve("entrypoint.lua")
        val moduleManager = ModuleManager()
        entrypoint.createNewFile()
        server = EngineServer(
            ServerId("test"),
            PlayerStorage(),
            AcousticSimulator.DUMMY,
            ServerPlatform.DUMMY,
            namespacedStorage,
            moduleManager,
            Thread.currentThread(),
            false,
            tempDir.resolve("world").toFile(),
            connectDatabase(tempDir.toString()),
            LuaScriptEngine(
                LuaScriptEngine.Dependencies(
                    LuaScriptEngine.globals(),
                    namespacedStorage,
                    LuaDataStorage(),
                    moduleManager,
                    scriptsPath.path,
                ),
                FileScriptSource(entrypoint)
            ),
            SaveTimers(
                SaveTimers.Counter(20),
                SaveTimers.Counter(20),
            )
        )
        transportContext = TestServerTransportContext(server)
        Injector.register<ServerTransportContext>(transportContext)
        server.simulation.loadWorld(
            World(
                WorldId("test"),
                server.simulation
            )
        )
    }

    @Test
    fun testServerPlayerInstantiation() {
        val playerId = PlayerId(UUID.randomUUID())
        val itemId = Uuid.next()
        val missingItemId = Uuid.next()
        val itemPrefabId = EngineId("test/item")
        runBlocking {
            server.database.saveEntity(
                EntityDatabaseKind.ITEM,
                EntityPersistentRecord(
                    itemId,
                    EntityPersistenceData.Item(itemPrefabId.stringRepresentation, 1, 64),
                    listOf(
                        SavingComponentSnapshot(
                            Item(itemId, itemPrefabId.toItemPrefabId()).snapshot(),
                            componentTypeOf(Item::class),
                        ).serializeToRecord(),
                    ),
                    emptyList(),
                )
            )
            val loading = async {
                server.playerLoader.loadPreparing(
                    EngineMinecraftServer.serverPlayerLoadSettings(
                        server,
                        playerId,
                        literalText("Test Player"),
                        server.simulation.defaultWorld.id
                    ).copy(inventoryItems = listOf(itemId, missingItemId)),
                    PlayerLoadSettings.Account(null)
                )
            }
            while (!loading.isCompleted) {
                server.update()
                delay(1)
            }
            loading.await()
        }

        val player = requireNotNull(server.playerStorage.get(playerId)) {
            "Player was not instantiated"
        }
        assertSame(server.simulation.defaultWorld, player.world)
        assertTrue(player.has<PlayerComponent>())
        assertEquals(2, player.items.size)
    }

    @Test
    fun asyncChunkLoadCanBeRequestedOffThread() {
        var failure: Throwable? = null
        val thread = Thread {
            runCatching {
                server.chunkPersistence.loadChunkAsync(
                    server.simulation.defaultWorld,
                    EngineChunkPos(0, 0),
                )
            }.onFailure { failure = it }
        }

        thread.start()
        thread.join()

        assertNull(failure)
    }
}
