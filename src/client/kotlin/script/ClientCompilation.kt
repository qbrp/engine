package org.lain.engine.client.script

import org.lain.engine.client.EngineClient
import org.lain.engine.script.compilation.Build

fun ClientLuaScriptEngine.compileScriptsOrThrow(): Build {
    return compileContents().successOrThrow()
}