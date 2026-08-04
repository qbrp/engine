package org.lain.engine.client.script

import org.lain.engine.client.EngineClient
import org.lain.engine.script.CompilationResult
import org.lain.engine.script.compileContents

class ClientCompilation(
    val luaContext: ClientLuaScriptEngine,
    val client: EngineClient
) {
    fun compileScripts(): CompilationResult {
        val contentsPath = client.resources.contents.file
        val result = compileContents(contentsPath, luaContext)
        return result
    }
}