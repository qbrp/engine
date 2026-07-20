package org.lain.engine.client.script

import org.lain.engine.client.EngineClient
import org.lain.engine.script.CompilationResult
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.script.compileContents
import org.lain.engine.script.emptyNamespacedStorage
import org.lain.engine.script.loadContentsCompileResult
import org.lain.engine.script.lua.LuaContext

class ClientCompilation(
    val luaContext: ClientLuaContext,
    val client: EngineClient
) {
    fun compileScripts(): CompilationResult {
        val contentsPath = client.resources.contents.file
        val result = compileContents(contentsPath, luaContext)
        return result
    }
}