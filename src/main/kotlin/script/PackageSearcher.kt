package org.lain.engine.script

import org.lain.engine.script.lua.varargsFunction
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue

fun Globals.setupScriptPackageSearcher() {
    get("package").get("searchers").set(4, varargsFunction { args ->
        val moduleName = args.checkjstring(1)
        val packagePath = get("package")
            .get("path")
            .tojstring()
        val resourceName = moduleName.replace('.', '/')
        val templates = packagePath.split(";")

        for (template in templates) {
            val path = template.replace("?", resourceName)

            try {
                val scriptSource = ResourceScriptSource(path)
                scriptSource.open().use { stream ->
                    val result = load(
                        stream.reader(),
                        scriptSource.chunkName
                    )

                    val func = result.arg1()

                    return@varargsFunction if (func.isfunction()) {
                        LuaValue.varargsOf(
                            func,
                            LuaValue.valueOf(scriptSource.chunkName)
                        )
                    } else {
                        LuaValue.varargsOf(
                            LuaValue.NIL,
                            LuaValue.valueOf(
                                "'${scriptSource.chunkName}': ${result.arg(2).tojstring()}"
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        LuaValue.varargsOf(
            LuaValue.NIL,
            LuaValue.valueOf(
                "\n\tno resource found for module '$moduleName'"
            )
        )
    })
}