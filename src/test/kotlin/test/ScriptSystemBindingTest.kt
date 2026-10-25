package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.EngineSimulation
import org.lain.engine.player.MovementStatus
import org.lain.engine.player.PlayerStorage
import org.lain.engine.script.EngineId
import org.lain.engine.script.FileScriptSource
import org.lain.engine.script.ModuleManager
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptSystem
import org.lain.engine.script.ScriptSystemId
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.SystemSide
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.script.tick
import org.lain.engine.script.lua.LuaDataStorage
import org.lain.engine.script.lua.LuaScript
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.library.LuaComponentAccess
import org.lain.engine.script.lua.library.ecs.MovementStatusBinding
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import java.nio.file.Path

class ScriptSystemBindingTest : EngineTest() {
    @field:TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun nativeBindingSystemUsesCurrentComponentInstance() {
        val namespaces = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())
        val entrypoint = temporaryDirectory.resolve("entrypoint.lua").toFile().apply {
            createNewFile()
        }
        val lua = LuaScriptEngine(
            LuaScriptEngine.Dependencies(
                globals = LuaScriptEngine.globals(),
                namespacesStorage = namespaces,
                dataStorage = LuaDataStorage(),
                moduleManager = ModuleManager(),
                scriptsPath = temporaryDirectory.toString(),
                writeCompilationManifest = false,
            ),
            FileScriptSource(entrypoint),
        )
        val simulation = EngineSimulation(
            isClient = true,
            extension = EngineSimulation.SimulationTickExtension.DUMMY,
            settings = EngineSimulation.Settings.DUMMY,
            players = PlayerStorage(),
            namespacedStorage = namespaces,
            scriptEngine = lua,
            thread = Thread.currentThread(),
            useAdventureMode = false
        )
        val world = World(WorldId("binding-test"), simulation)
        simulation.loadWorld(world)

        val entity = world.componentManager.addEntity()
        val replaced = MovementStatus(stamina = 0.9f)
        val current = MovementStatus(stamina = 0.6f)
        with(world) {
            entity.setComponent(replaced)
            entity.setComponent(current)
        }

        val tickFunction = lua.globals.load(
            "return function(_, _, movement) movement.stamina = 0.25 end"
        ).call().checkfunction()
        val system = ScriptSystem(
            id = ScriptSystemId(EngineId("test/native_binding")),
            query = listOf(LuaComponentAccess.Native(MovementStatusBinding)),
            side = SystemSide.BOTH,
            entityHandleScript = LuaScript<ScriptContext.SystemEntityHandle, ScriptValue>(
                lua,
                tickFunction,
            ),
        )

        system.tick(world)

        val stored = with(world) { entity.getComponent<MovementStatus>() }
        assertSame(current, stored)
        assertEquals(0.25f, current.stamina)
        assertEquals(0.9f, replaced.stamina)
    }
}
