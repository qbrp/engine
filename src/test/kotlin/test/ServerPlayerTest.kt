package org.lain.engine.test

import kotlinx.coroutines.runBlocking
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
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.script.lua.LuaDataStorage
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerId
import org.lain.engine.server.ServerPlatform
import org.lain.engine.storage.SaveTimers
import org.lain.engine.storage.connectDatabase
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.util.Injector
import org.lain.engine.world.World
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
        entrypoint.createNewFile()
        server = EngineServer(
            ServerId("test"),
            PlayerStorage(),
            AcousticSimulator.DUMMY,
            ServerPlatform.DUMMY,
            namespacedStorage = namespacedStorage,
            Thread.currentThread(),
            false,
            tempDir.resolve("world").toFile(),
            connectDatabase(tempDir.toString()),
            LuaScriptEngine(
                LuaScriptEngine.Dependencies(
                    LuaScriptEngine.globals(),
                    namespacedStorage,
                    scriptsPath.path,
                    LuaDataStorage()
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
        runBlocking {
            server.playerLoader.loadPreparing(
                EngineMinecraftServer.serverPlayerLoadSettings(
                    server,
                    playerId,
                    literalText("Test Player"),
                    server.simulation.defaultWorld.id
                ),
                PlayerLoadSettings.Account(null)
            )
        }

        val player = requireNotNull(server.playerStorage.get(playerId)) {
            "Player was not instantiated"
        }
        assertSame(server.simulation.defaultWorld, player.world)
        assertTrue(player.has<PlayerComponent>())
    }
}
