package org.lain.engine.test

import org.junit.jupiter.api.BeforeAll
import org.lain.engine.EngineSimulation
import org.lain.engine.bootstrap
import org.lain.engine.item.ItemAssets
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.ItemProgressionAnimations
import org.lain.engine.player.PlayerStorage
import org.lain.engine.player.interaction.PlayerInput
import org.lain.engine.player.interaction.PlayerInputMode
import org.lain.engine.script.FileScriptSource
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptEngine
import org.lain.engine.script.ScriptSource
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.script.lua.LuaDataStorage
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.util.Storage
import org.lain.engine.util.component.ComponentTypeRegistry
import org.lain.engine.util.component.ComponentWorld
import org.lain.engine.util.component.registerAll
import java.io.File
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
    ItemId("dummy"),
    1,
    "Dummy Item Name",
    ItemAssets.withDefaultAsset("dummy"),
    ItemProgressionAnimations(mapOf()),
    { null },
    { emptyList() }
)

abstract class EngineTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll(): Unit {
            bootstrap()
        }
    }
}