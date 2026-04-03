package org.lain.engine.script

import org.lain.engine.player.*
import org.lain.engine.storage.addIfNotNull
import org.lain.engine.util.NamespaceId
import org.lain.engine.util.Storage
import org.lain.engine.world.SoundEventId
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

private val LUA = JsePlatform.standardGlobals()

data class LuaCompilationContext(
    val namespaces: MutableMap<NamespaceId, CompiledNamespace> = mutableMapOf(),
    val errors: MutableList<Throwable> = mutableListOf(),
)

class LuaContext(
    val playerStorage: Storage<PlayerId, EnginePlayer>,
    val scriptsPath: File,
    directory: File
) {
    var compilationFunction: LuaFunction? = null
    val callbacksFunctions: MutableList<LuaFunction> = mutableListOf()
    init {
        directory.parentFile.mkdirs()
        if (directory.exists()) {
            LUA.setup()
            LUA.loadfile(directory.path).call()
        }
    }

    fun compileCallbacks(): Callbacks {
        val playerInstantiate = mutableListOf<PlayerInstantiateCallback>()
        val playerDestroy = mutableListOf<PlayerDestroyCallback>()
        val worldTickSecond = mutableListOf<WorldTickSecondCallback>()
        val worldTick = mutableListOf<WorldTickCallback>()

        callbacksFunctions.forEach {
            val table = it.call().checktable()

            fun <C : ScriptContext, R : Any> MutableList<ScriptCallback<C, R>>.addTableCallback(name: String) {
                addIfNotNull {
                    val script = table.get(name).nullable()?.checkfunction()
                        ?.let { function -> LuaScript<C, R>(function) } ?: return@addIfNotNull null
                    ScriptCallback(listOf(script))
                }
            }

            playerInstantiate.addTableCallback("player_instantiate")
            playerDestroy.addTableCallback("player_destroy")
            worldTickSecond.addTableCallback("world_tick_second")
            worldTick.addTableCallback("world_tick")
        }

        return Callbacks(
            ScriptCallback(playerInstantiate.flatMap { it.scripts }),
            ScriptCallback(playerDestroy.flatMap { it.scripts }),
            ScriptCallback(worldTickSecond.flatMap { it.scripts }),
            ScriptCallback(worldTick.flatMap { it.scripts })
        )
    }

    fun compileContents(): CompilationResult {
        val compilationFunction = compilationFunction ?: return CompilationResult(mapOf())
        val namespaceTable = compilationFunction.call().checktable()
        val compilation = LuaCompilationContext()
        namespaceTable.get("namespaces").checktable()
            .toList { it.checktable() }
            .forEach { namespace ->
                val namespaceId = runCatching { NamespaceId(namespace.get("id").tojstring()) }.getOrNull() ?: return@forEach

                try {
                    val itemsArray = namespace.get("items").nullable()?.checktable()
                    val scriptsArray = namespace.get("scripts").nullable()?.checktable()

                    val items = mutableListOf<CompiledNamespace.Item>()
                    val scripts = mutableMapOf<ScriptId, LuaScript<*, *>>()

                    itemsArray?.toList { it.checktable() }
                        ?.forEach { item ->
                            val itemId = item.get("id").tojstring()
                            val displayName = item.get("display_name").tojstring()
                            val stackSize = item.get("stack_size").nullable()?.toint()
                            val mass = item.get("mass").nullable()?.tofloat()
                            val tooltip = item.get("tooltip").nullable()?.tojstring()
                            val writable = item.get("writable").nullable()?.checktable()?.let {
                                WritableConfig(
                                    it.get("pages").toint(),
                                    it.get("texture")?.nullable()?.tojstring(),
                                )
                            }
                            val flashlight = item.get("flashlight").nullable()?.checktable()?.let {
                                FlashlightConfig(
                                    it.get("radius").nullable()?.tofloat() ?: 8f,
                                    it.get("distance").nullable()?.tofloat() ?: 16f,
                                    it.get("light").nullable()?.tofloat() ?: 15f,
                                )
                            }

                            val assets = item.get("assets").nullable()?.checktable()?.toStringMap()
                            val progressionAnimations = item.get("progression_animations").nullable()?.checktable()
                                ?.toMap { ProgressionAnimationId(it.tojstring()) }
                            val soundEvents = item.get("sound_events").nullable()?.checktable()
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

                            LOGGER.debug("Загружен предмет {}", itemId)
                        }

                    scriptsArray?.toList { it.checktable() }
                        ?.forEach { script ->
                            val id = script.get("id").tojstring()
                            val function = script.get("fun").checkfunction()
                            scripts[id.toScriptId()] = LuaScript<ScriptContext, Any>(function)
                        }

                    compilation.namespaces[namespaceId] = CompiledNamespace(
                        items.associateBy { it.id },
                        mapOf(),
                        mapOf(),
                        scripts
                    )
                } catch (e: Throwable) {
                    compilation.errors += e
                    logNamespaceCompilationError(namespaceId, e)
                }
            }
        return CompilationResult(compilation.namespaces)
    }
}

context(ctx: LuaContext)
private fun Globals.setup() {
    set("SCRIPTS_PATH", ctx.scriptsPath.path)
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
    set("_callbacks", object : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue? {
            ctx.callbacksFunctions.add(arg.checkfunction())
            return NIL
        }
    })
    set("_set_player_custom_max_speed", object : TwoArgFunction() {
        override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue? {
            val player = ctx.getPlayer(arg1)
            val speed = arg2.tofloat()
            player.setCustomMaxSpeed(speed)
            return NIL
        }
    })
    set("_reset_player_custom_max_speed", object : OneArgFunction() {
        override fun call(arg1: LuaValue): LuaValue? {
            val player = ctx.getPlayer(arg1)
            player.resetCustomMaxSpeed()
            return NIL
        }
    })
    set("_player_narration", object : TwoArgFunction() {
        override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue? {
            val player = ctx.getPlayer(arg1)
            val narration = arg2.checktable()
            player.serverNarration(
                narration.get("message").tojstring(),
                narration.get("time").toint(),
                narration.get("kick").nullable()?.toboolean() ?: false,
            )
            return NIL
        }
    })
}

private fun LuaContext.getPlayer(playerIdArg: LuaValue): EnginePlayer {
    val playerId = PlayerId.fromString(playerIdArg.tojstring())
    return playerStorage.get(playerId) ?: kotlin.error("Player $playerId not found")
}