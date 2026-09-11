package org.lain.engine.script.lua.library

import org.lain.engine.Constants
import org.lain.engine.script.ScriptEngine
import org.lain.engine.script.lua.NIL
import org.lain.engine.script.lua.luaTable
import org.luaj.vm2.Varargs
import org.slf4j.spi.LoggingEventBuilder

fun debugScript(module: String, info: String) {
    if (Constants.DEBUG_ALL) {
        ScriptEngine.LOGGER.info("[$module] $info")
    }
}

private fun log(varargs: Varargs, builder: LoggingEventBuilder) {
    val str = varargs.arg(1).tojstring()
    val arguments = varargs.narg() - 1
    builder.setMessage(str)
    for (i in 1..arguments) {
        builder.addArgument(varargs.arg(i + 1).tojstring())
    }
    builder.log()
}

fun LoggerTable() = luaTable {
    function("info") { varargs ->
        log(varargs, ScriptEngine.LOGGER.atInfo())
        NIL
    }
    function("warn") { varargs ->
        log(varargs, ScriptEngine.LOGGER.atWarn())
        NIL
    }
    function("error") { varargs ->
        log(varargs, ScriptEngine.LOGGER.atError())
        NIL
    }
    function2("debug") { module, str ->
        debugScript(module.tojstring(), str.tojstring())
        NIL
    }
}