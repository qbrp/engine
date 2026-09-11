package org.lain.engine.script

import org.lain.engine.player.EnginePlayer
import org.lain.engine.world.World
import org.slf4j.LoggerFactory

interface ScriptEngine {
    fun createScriptComponent(value: ScriptValue, type: ScriptComponentType): ScriptComponent
    fun reloadScript(moduleName: String)

    fun updateModules(modules: Modules)

    fun tickBeforeCallbacks(world: World)
    fun tick(world: World)

    fun setupPlayer(player: EnginePlayer)

    fun loadWorld(world: World)

    object Dummy : ScriptEngine {
        override fun createScriptComponent(
            value: ScriptValue,
            type: ScriptComponentType
        ): ScriptComponent {
            throw NotImplementedError()
        }

        override fun reloadScript(moduleName: String) {}
        override fun updateModules(modules: Modules) {}

        override fun tickBeforeCallbacks(world: World) {}
        override fun tick(world: World) {}
        override fun setupPlayer(player: EnginePlayer) {}
        override fun loadWorld(world: World) {}
    }

    companion object {
        val LOGGER = LoggerFactory.getLogger("Script Engine")
    }
}
