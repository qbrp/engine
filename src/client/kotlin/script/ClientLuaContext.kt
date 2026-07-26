package org.lain.engine.client.script

import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.render.ui.webPageUrl
import org.lain.engine.script.CallbackType
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.lua.LuaContext
import org.lain.engine.script.lua.LuaDependencies
import org.lain.engine.script.lua.LuaRuntimeDependencies
import org.lain.engine.script.lua.ScriptSource
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.luaTableOf
import org.lain.engine.script.lua.toLuaValue
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class ClientLuaContext(
    val client: EngineClient,
    entrypoint: ScriptSource,
    dependencies: LuaDependencies,
) : LuaContext(dependencies, entrypoint) {
    val audioSourceTable = LuaTable()
    val webTable = WebTable()
    lateinit var gameSessionTable: LuaTable

    override fun listCallbackTypes(): List<CallbackType<out ScriptContext>> {
        return super.listCallbackTypes() + ClientCallbacks.list()
    }

    override fun mapScriptContext(context: ScriptContext): LuaValue {
        return when(context) {
            is ClientScriptContext.WorkspaceOpen -> {
                luaTable {
                    "windows" {
                        context.screen.windows.forEach { (id, window) ->
                            id {
                                webWidgetBehaviour { window }
                                "game_session"(gameSessionTable)
                            }
                        }
                    }
                    functionV("add_window") { args ->
                        val window = context.screen.addWindow(
                            args.arg(2).tojstring(),
                            webPageUrl(args.arg(3).tojstring()),
                            args.arg(4).toint(),
                            args.arg(5).toint()
                        )
                        luaTable {
                            webWidgetBehaviour { window }
                            "game_session"(gameSessionTable)
                        }
                    }
                }
            }
            else -> super.mapScriptContext(context)
        }
    }

    override fun setupTables() {
        super.setupTables()
        globals.set("AudioSource", audioSourceTable)
        globals.set("Web", webTable)
    }

    fun setupClientGameSession(gameSession: GameSession) {
        val world = gameSession.world
        setupGame(
            LuaRuntimeDependencies(gameSession.playerStorage, mutableMapOf(world.id to world))
        )
        gameSessionTable = GameSessionTable(gameSession)
        globals.setupAudio()
        globals.setupKeyMappings()
        globals.set("GameSession", gameSessionTable)
        loadWorld(world)
    }
}
