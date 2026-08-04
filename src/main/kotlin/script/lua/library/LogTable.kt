package org.lain.engine.script.lua.library

import org.lain.engine.Constants
import org.lain.engine.script.SCRIPT_LOGGERRR
import org.lain.engine.script.lua.NIL
import org.lain.engine.script.lua.luaTable

fun debugScript(module: String, info: String) {
    if (Constants.DEBUG_ALL) {
        SCRIPT_LOGGERRR.info("[$module] $info")
    }
}

fun LogTable() = luaTable {
    function1("info") { str ->
        SCRIPT_LOGGERRR.info(str.tojstring())
        NIL
    }
    function2("debug") { module, str ->
        debugScript(module.tojstring(), str.tojstring())
        NIL
    }
}