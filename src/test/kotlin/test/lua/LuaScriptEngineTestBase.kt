package org.lain.engine.test.lua

import org.lain.engine.script.ModuleManager
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.ResourceScriptSource
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.script.lua.LuaDataStorage
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.test.EngineTest

abstract class LuaScriptEngineTestBase : EngineTest() {
    protected fun createLuaEngine(entrypointResource: String): LuaScriptEngine {
        val namespaces = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())
        return LuaScriptEngine(
            LuaScriptEngine.Dependencies(
                globals = LuaScriptEngine.globals(),
                namespacesStorage = namespaces,
                dataStorage = LuaDataStorage(),
                moduleManager = ModuleManager(),
                scriptsPath = "lua",
                writeCompilationManifest = false,
            ),
            ResourceScriptSource(entrypointResource),
        ).also { it.setup() }
    }
}
