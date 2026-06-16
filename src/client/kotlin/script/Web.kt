package org.lain.engine.client.script

import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.ui.WebScreen
import org.lain.engine.client.render.ui.WebWidgetScreenParameters
import org.lain.engine.client.render.ui.WebWidgetSizeParameters
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.nullable
import org.lain.engine.script.lua.toLuaValue
import org.luaj.vm2.LuaValue
import java.util.concurrent.CompletableFuture

context(lua: ClientLuaContext)
fun WebTable() = luaTable {
    function3("open_screen") { urlL, parametersL, resolverL ->
        val url = urlL.tojstring()
        val resolverFunction = resolverL.checkfunction()
        val resolver: (Int, Int) -> WebWidgetSizeParameters = { screenWidth, screenHeight ->
            val varargs = resolverFunction.invoke(
                screenWidth.toLuaValue(),
                screenHeight.toLuaValue()
            )
            WebWidgetSizeParameters(
                varargs.arg(1).toint(),
                varargs.arg(2).toint(),
                varargs.arg(3).toint(),
                varargs.arg(4).toint()
            )
        }
        val parameters = parametersL.checktable()
        val pause = parameters.get("pause").nullable()?.toboolean() ?: false
        val background = parameters.get("background").nullable()?.toboolean() ?: true
        val screen = WebScreen(lua.client.resources, url, WebWidgetScreenParameters(pause, background), resolver)
        val bridge by lazy { screen.widget.bridge() }
        MinecraftClient.setScreen(screen)
        luaTable {
            "url"(url)
            function1("on_ready") { resolverL ->
                val resolver = resolverL.checkfunction()
                val subscribe = bridge.onReady { resolver.invoke() }
                screen.unsubscribes += { subscribe.unsubscribe() }
                LuaValue.NIL
            }
            function2("on_event") { idL, resolverL ->
                val resolver = resolverL.checkfunction()
                val id = idL.tojstring()
                val subscribe = bridge.onEvent(id) { channel, payloadJson ->
                    resolver.invoke(channel.toLuaValue(), payloadJson.toLuaValue())
                }
                screen.unsubscribes += { subscribe.unsubscribe() }
                LuaValue.NIL
            }
            function2("on_request") { idL, resolverL ->
                val resolver = resolverL.checkfunction()
                val id = idL.tojstring()
                val subscribe = bridge.onRequest(id) { channel, payloadJson ->
                    CompletableFuture.completedFuture(
                        try {
                            resolver.invoke(channel.toLuaValue(), payloadJson.toLuaValue()).tojstring()
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            throw e
                        }
                    )
                }
                screen.unsubscribes += { subscribe.unsubscribe() }
                LuaValue.NIL
            }
            function2("emit") { idL, payloadL ->
                val id = idL.tojstring()
                val payload = payloadL.tojstring()
                bridge.emit(id, payload)
                LuaValue.NIL
            }
            function0("close") {
                screen.onClose()
                LuaValue.NIL
            }
            function1("on_close") { resolverL ->
                screen.onClose = { resolverL.checkfunction().call() }
                LuaValue.NIL
            }
        }
    }
}