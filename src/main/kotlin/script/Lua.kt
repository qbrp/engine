package org.lain.engine.script

import org.lain.engine.player.ProgressionAnimationId
import org.lain.engine.util.NamespaceId
import org.lain.engine.world.SoundEventId
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import org.slf4j.LoggerFactory
import java.io.File

private val LUA = JsePlatform.standardGlobals()
private val LOGGER = LoggerFactory.getLogger("Engine Lua")

data class LuaCompilationContext(val namespaces: MutableMap<NamespaceId, CompiledNamespace> = mutableMapOf())

data class LuaContext(
    val scriptsPath: File,
    val directory: File
) {
    init {
        LUA.setup(scriptsPath)
        LUA.loadfile(directory.path).call()
    }
    var compilationFunction: LuaFunction? = null

    fun executeCompilation(): CompilationResult {
        val compilationFunction = compilationFunction ?: return CompilationResult(mapOf())
        val namespaceTable = compilationFunction.call().checktable()
        val compilation = LuaCompilationContext()
        namespaceTable.get("namespaces").checktable()
            .toList { it.checktable() }
            .forEach { namespace ->
                val namespaceId = NamespaceId(namespace.get("id").tojstring())
                val itemsTable = namespace.get("items").checktable()

                val items = mutableListOf<CompiledNamespace.Item>()

                for (key in itemsTable.keys()) {
                    val itemId = key.tojstring()
                    val itemTable = itemsTable.get(key).checktable()
                    val displayName = itemTable.get("display_name").tojstring()
                    val stackSize = itemTable.get("stack_size").nullable()?.toint()
                    val mass = itemTable.get("mass").nullable()?.tofloat()
                    val tooltip = itemTable.get("tooltip").nullable()?.tojstring()
                    val writable = itemTable.get("writable").nullable()?.checktable()?.let {
                        WritableConfig(
                            it.get("pages").toint(),
                            it.get("texture")?.nullable()?.tojstring(),
                        )
                    }
                    val flashlight = itemTable.get("flashlight").nullable()?.checktable()?.let {
                        FlashlightConfig(
                            it.get("radius").nullable()?.tofloat() ?: 8f,
                            it.get("distance").nullable()?.tofloat() ?: 16f,
                            it.get("light").nullable()?.tofloat() ?: 15f,
                        )
                    }

                    val assets = itemTable.get("assets").nullable()?.checktable()?.toStringMap()
                    val progressionAnimations = itemTable.get("assets").nullable()?.checktable()
                        ?.toMap { ProgressionAnimationId(it.tojstring()) }
                    val soundEvents = itemTable.get("sound_events").nullable()?.checktable()
                        ?.toMap { SoundEventId(it.tojstring()) }

                    items + CompiledItem(
                        namespaceId,
                        itemId,
                        displayName,
                        assets,
                        progressionAnimations,
                        soundEvents,
                        stackSize,
                        mass,
                        tooltip,
                        writable,
                        flashlight,
                    )

                    LOGGER.debug("Загружен предмет {}", key)
                }

                compilation.namespaces[namespaceId] = CompiledNamespace(
                    items.associateBy { it.id },
                    mapOf(),
                    mapOf()
                )
            }
        return CompilationResult(compilation.namespaces)
    }
}

context(ctx: LuaContext)
private fun Globals.setup(directory: File) {
    set("SCRIPTS_PATH", directory.path)
    set("_info", object : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue {
            LOGGER.info(arg.tojstring())
            return NIL
        }
    })
    set("_compilation", object : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue? {
            ctx.compilationFunction = arg.checkfunction()
            return NIL
        }
    })
}