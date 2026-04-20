package org.lain.engine.client.script

import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.script.lua.LuaContext
import org.lain.engine.script.lua.LuaDependencies
import org.lain.engine.script.lua.LuaRuntimeDependencies
import org.luaj.vm2.LuaTable

class ClientLuaContext(
    val client: EngineClient,
    dependencies: LuaDependencies,
) : LuaContext(dependencies) {
    lateinit var audioSourceTable: LuaTable

    override fun setupTables() {
        super.setupTables()
        audioSourceTable = globals.get("AudioSource").checktable()
    }

    override fun setupGlobalsRuntime() {
        super.setupGlobalsRuntime()
        globals.setupAudio()
    }

    fun setupClientGameSession(gameSession: GameSession) {
        val world = gameSession.world
        setupGame(
            LuaRuntimeDependencies(gameSession.playerStorage, mutableMapOf(world.id to world))
        )
    }
}