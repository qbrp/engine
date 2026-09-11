package org.lain.engine.client.script

import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.ui.WebScreen
import org.lain.engine.client.render.ui.WebWidgetScreenParameters
import org.lain.engine.client.render.ui.WebWidgetSizeParameters
import org.lain.engine.script.lua.LuaTableBuilder
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.nullable
import org.lain.engine.script.lua.luaNum
import org.lain.engine.script.lua.luaStr
import org.luaj.vm2.LuaValue
import tytoo.grapheneui.api.widget.GrapheneWebViewWidget
import java.util.concurrent.CompletableFuture

context(lua: ClientLuaScriptEngine)
fun WebTable() = luaTable {
    function3("open_screen") { urlL, parametersL, resolverL ->
        val url = urlL.tojstring()
        val resolverFunction = resolverL.checkfunction()
        val resolver: (Int, Int) -> WebWidgetSizeParameters = { screenWidth, screenHeight ->
            val varargs = resolverFunction.invoke(
                screenWidth.toDouble().luaNum(),
                screenHeight.toDouble().luaNum()
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
        MinecraftClient.setScreen(screen)
        luaTable {
            webWidgetBehaviour { screen.widget }
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

fun LuaTableBuilder.webWidgetBehaviour(widgetGetter: () -> GrapheneWebViewWidget) {
    val bridge by lazy { widgetGetter().bridge() }

    "url"(widgetGetter().currentUrl())
    function1("on_ready") { resolverL ->
        val resolver = resolverL.checkfunction()
        bridge.onReady { resolver.invoke() }
        LuaValue.NIL
    }
    function2("on_event") { idL, resolverL ->
        val resolver = resolverL.checkfunction()
        val id = idL.tojstring()
        val subscribe = bridge.onEvent(id) { channel, payloadJson ->
            resolver.invoke(channel.luaStr(), payloadJson.luaStr())
        }
        LuaValue.NIL
    }
    function2("on_request") { idL, resolverL ->
        val resolver = resolverL.checkfunction()
        val id = idL.tojstring()
        val subscribe = bridge.onRequest(id) { channel, payloadJson ->
            CompletableFuture.completedFuture(
                try {
                    resolver.invoke(channel.luaStr(), payloadJson.luaStr()).tojstring()
                } catch (e: Throwable) {
                    e.printStackTrace()
                    throw e
                }
            )
        }
        LuaValue.NIL
    }
    function2("emit") { idL, payloadL ->
        val id = idL.tojstring()
        val payload = payloadL.tojstring()
        bridge.emit(id, payload)
        LuaValue.NIL
    }
}