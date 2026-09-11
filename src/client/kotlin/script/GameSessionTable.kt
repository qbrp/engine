package org.lain.engine.client.script

import org.lain.engine.client.GameSession
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.script.lua.luaTable

context(lua: ClientLuaScriptEngine)
fun GameSessionTable(gameSession: GameSession) = luaTable {
    "main_player"(gameSession.mainPlayer.coerceToLua())
    "audio"(lua.audioLibrary.library)
}