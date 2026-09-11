package org.lain.engine.client.script

import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.render.ui.webPageUrl
import org.lain.engine.client.transport.ClientContext
import org.lain.engine.script.CallbackType
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptSource
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.library.luaEntity
import org.lain.engine.script.lua.library.luaWorld
import org.lain.engine.script.lua.luaTable
import org.lain.engine.world.World
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class ClientLuaScriptEngine(
    val client: EngineClient,
    entrypoint: ScriptSource,
    dependencies: Dependencies,
) : LuaScriptEngine(dependencies, entrypoint) {
    val audioLibrary = AudioLibrary(client.audioManager)
    val webTable = WebTable()
    lateinit var gameSessionTable: LuaTable

    override fun tickBeforeCallbacks(world: World) = with(world) {
        super.tickBeforeCallbacks(world)
        applyLuaEntityRpcQueues()
    }

    override fun listCallbackTypes(): List<CallbackType<out ScriptContext, out ScriptValue>> {
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
            is ClientScriptContext.ItemTooltip -> with(context.world) {
                luaTable {
                    "world"(context.world.luaWorld())
                    "item"(context.item.luaEntity())
                }
            }
            else -> super.mapScriptContext(context)
        }
    }

    fun setupClientGameSession(gameSession: GameSession) {
        val world = gameSession.world
        setupGame(RuntimeDependencies(gameSession.simulation))
        gameSessionTable = GameSessionTable(gameSession)
        engineTable["game_session"] = gameSessionTable
        engineTable["web"] = webTable
        loadWorld(world)
    }
}
