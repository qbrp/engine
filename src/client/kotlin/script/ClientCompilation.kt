package org.lain.engine.client.script

import org.lain.engine.client.EngineClient
import org.lain.engine.script.compilation.Build

class ClientCompilation(
    val luaContext: ClientLuaScriptEngine,
    val client: EngineClient
) {
    fun compileScriptsOrThrow(): Build {
        return luaContext.compileContents().successOrThrow()
    }
}