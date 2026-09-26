package org.lain.engine.test

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.EngineSimulation
import org.lain.engine.bootstrap
import org.lain.engine.chat.acoustic.AcousticSimulator
import org.lain.engine.container.EntriesDirty
import org.lain.engine.data.SaveTimers
import org.lain.engine.data.connectDatabase
import org.lain.engine.item.ItemAssets
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.ItemProgressionAnimations
import org.lain.engine.player.PlayerStorage
import org.lain.engine.player.interaction.PlayerInputMode
import org.lain.engine.script.EngineId
import org.lain.engine.script.FileScriptSource
import org.lain.engine.script.ModuleManager
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptEngine
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.script.lua.LuaDataStorage
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerId
import org.lain.engine.server.ServerPlatform
import org.lain.engine.server.replication.Networked
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.util.Injector
import org.lain.engine.util.Storage
import org.lain.engine.util.ecs.ComponentWorld
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.math.MutableEVec3
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

fun TestEngineSimulation(
    isClient: Boolean = false,
    tickExtension: EngineSimulation.SimulationTickExtension = EngineSimulation.SimulationTickExtension.DUMMY,
    settings: EngineSimulation.Settings = EngineSimulation.Settings.DUMMY,
    namespacedStorage: NamespacedStorageAccess = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())
) = EngineSimulation(
    isClient,
    tickExtension,
    settings,
    PlayerStorage(),
    namespacedStorage,
    ScriptEngine.Dummy,
    Thread.currentThread(),
    PlayerInputMode.Authoritative
)

fun TestComponentWorld(
    registerEngineKotlinComponents: Boolean = false
): ComponentWorld {
    return ComponentWorld(
        Thread.currentThread(),
        ConcurrentHashMap(),
        Storage(),
        registerEngineKotlinComponents
    )
}

fun DummyItemPrefab() = ItemPrefab(
    ItemId(EngineId("dummy")),
    1,
    "Dummy Item Name",
    ItemAssets.withDefaultAsset(EngineId("dummy")),
    ItemProgressionAnimations(mapOf()),
    {}
)

fun setupTestEngineServer(tempDir: Path): EngineServer {
    val namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())
    val scriptsPath = tempDir.resolve("scripts").toFile()
    scriptsPath.mkdirs()
    val entrypoint = scriptsPath.resolve("entrypoint.lua")
    val moduleManager = ModuleManager()
    entrypoint.createNewFile()
    return EngineServer(
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
    ).also { server ->
        Injector.register<ServerTransportContext>(TestServerTransportContext(server))
        server.simulation.loadWorld(
            World(
                WorldId("test"),
                server.simulation
            )
        )
    }
}

abstract class EngineTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll(): Unit {
            bootstrap()
        }
    }
}