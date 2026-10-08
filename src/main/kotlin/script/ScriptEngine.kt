package org.lain.engine.script

import org.lain.engine.player.EnginePlayer
import org.lain.engine.world.World
import org.slf4j.LoggerFactory

interface ScriptEngine {
    fun createScriptComponent(value: ScriptValue, type: ScriptComponentType): ScriptComponent
    fun reloadScript(moduleName: String)

    fun updateModules(modules: Modules)

    fun tickPush(world: World)
    fun tickPull(world: World)
    fun tickVerbLookup(world: World)

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

        override fun tickPush(world: World) {}
        override fun tickPull(world: World) {}
        override fun tickVerbLookup(world: World) {}

        override fun setupPlayer(player: EnginePlayer) {}
        override fun loadWorld(world: World) {}
    }

    companion object {
        val LOGGER = LoggerFactory.getLogger("Script Engine")
    }
}
