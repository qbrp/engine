package org.lain.engine.client.script

import org.lain.engine.client.EngineClient
import org.lain.engine.script.lua.LuaContext
import org.lain.engine.script.lua.LuaDependencies
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

    override fun setupGlobals() {
        super.setupGlobals()
        globals.setupAudio()
    }
}