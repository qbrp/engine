package org.lain.engine.client.script

import org.lain.engine.client.GameSession
import org.lain.engine.script.lua.LuaContext
import org.lain.engine.script.lua.coerceToLua
import org.lain.engine.script.lua.luaTable

context(lua: LuaContext)
fun GameSessionTable(gameSession: GameSession) = luaTable {
    "main_player"(gameSession.mainPlayer.coerceToLua())
}