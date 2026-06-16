package org.lain.engine.client.script

import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.script.lua.LuaContext
import org.lain.engine.script.lua.LuaDependencies
import org.lain.engine.script.lua.LuaRuntimeDependencies
import org.lain.engine.script.lua.ScriptSource
import org.luaj.vm2.LuaTable

class ClientLuaContext(
    val client: EngineClient,
    entrypoint: ScriptSource,
    dependencies: LuaDependencies,
) : LuaContext(dependencies, entrypoint) {
    val audioSourceTable = LuaTable()
    val webTable = WebTable()

    override fun setupTables() {
        super.setupTables()
        globals.set("AudioSource", audioSourceTable)
        globals.set("Web", webTable)
    }

    fun setupClientGameSession(gameSession: GameSession) {
        val world = gameSession.world
        setupGame(
            LuaRuntimeDependencies(gameSession.playerStorage, mutableMapOf(world.id to world))
        )
        globals.setupAudio()
        globals.setupKeyMappings()
        loadWorld(world)
    }
}