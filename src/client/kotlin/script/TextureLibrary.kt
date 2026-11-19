package org.lain.engine.client.script

import org.lain.engine.script.EngineId
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.NIL
import org.lain.engine.script.lua.library.luaId
import org.lain.engine.script.lua.library.toLook
import org.lain.engine.script.lua.luaTable

private data class LookTextureReference(
    val engineId: EngineId?
)

context(lua: ClientLuaScriptEngine)
private fun LookTextureReferenceType() = LuaUserdataType<LookTextureReference>() {
    indexSelf { self, key ->
        if (key.tojstring() == "id") self.engineId?.luaId() ?: NIL else NIL
    }
}

context(lua: ClientLuaScriptEngine)
fun TextureLibrary() {
    val type = LookTextureReferenceType()
    luaTable {
        function1("look") { look ->
            type.newInstance(
                LookTextureReference(
                    lua.client.lookTextureManager.getOrDownloadTextureNullable(
                        look.checktable().toLook()
                    )
                        ?.let { EngineId.parse(it.path) }
                )
            )
        }
    }
}