package org.lain.engine.script.lua

import org.lain.cyberia.ecs.ComponentType
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.script.*
import org.lain.engine.script.yaml.namespacedId
import org.lain.engine.storage.addIfNotNull
import org.lain.engine.util.Intent
import org.lain.engine.util.IntentId
import org.lain.engine.util.NamespaceId
import org.lain.engine.util.Storage
import org.lain.engine.util.file.BUILTIN_SCRIPTS_DIR
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.io.File

data class LuaCompilationContext(
    val namespaces: MutableMap<NamespaceId, CompiledNamespace> = mutableMapOf(),
    val errors: MutableList<CompilationException> = mutableListOf(),
)

class LuaDataStorage {
    private data class DataKey(val module: String, val id: String)
    private val values: MutableMap<DataKey, LuaValue> = mutableMapOf()

    fun get(module: String, slot: String) = values[DataKey(module, slot)]

    fun set(module: String, id: String, value: LuaValue) {
        values[DataKey(module, id)] = value
    }

    fun getOrDefault(module: String, id: String, default: LuaValue): LuaValue {
        return values.computeIfAbsent(DataKey(module, id)) { default }
    }
}

data class LuaDependencies(
    val globals: Globals,
    val namespacesStorage: NamespacedStorage,
    val scriptsPath: File,
    val dataStorage: LuaDataStorage,
)

data class LuaRuntimeDependencies(
    val playerStorage: Storage<PlayerId, EnginePlayer>,
    val worlds: MutableMap<WorldId, World>
)

open class LuaContext(val dependencies: LuaDependencies) {
    var runtimeDependencies: LuaRuntimeDependencies? = null
        private set
    val globals get() = dependencies.globals
    val scriptsPath get() = dependencies.scriptsPath

    var compilationFunctions: MutableList<LuaFunction> = mutableListOf()
    val callbacksFunctions: MutableList<LuaFunction> = mutableListOf()
    lateinit var playerTable: LuaTable
    lateinit var worldTable: LuaTable

    open fun setupTables() {
        playerTable = globals.get("Player").checktable()
        worldTable = globals.get("World").checktable()
    }

    open fun setupGlobalsRuntime() {}

    open fun setupGlobals() {
        globals.setupPlayer()
        globals.setupWorld()
    }

    open fun setup(directory: File) {
        directory.parentFile.mkdirs()

        val libraryDir = BUILTIN_SCRIPTS_DIR
        val libraryBootDir = libraryDir.resolve("core/boot.lua")

        require(directory.exists()) { "Входной скрипт сервера по директроии $directory не найден" }
        require(libraryBootDir.exists()) { "Скрипт загрузки стандартных библиотек $libraryBootDir не найден" }
        globals.setup()

        // Загрузка стандартной библиотеки
        globals.loadfile(libraryBootDir.path).call()
        setupTables()
        setupGlobals()

        globals.loadfile(directory.path).call()
        // Загружаем второй раз, чтобы... чтобы всё точно загрузилось. Без повторения иногда всё ломается
        setupTables()
    }

    open fun setupGame(dependencies: LuaRuntimeDependencies) {
        runtimeDependencies = dependencies
        setupGlobalsRuntime()
    }

    private fun compileCallbacks(): Callbacks {
        val playerInstantiate = mutableListOf<PlayerInstantiateCallback>()
        val playerDestroy = mutableListOf<PlayerDestroyCallback>()
        val worldTickSecond = mutableListOf<WorldTickSecondCallback>()
        val worldTick = mutableListOf<WorldTickCallback>()
        val placeVoxel = mutableListOf<PlaceVoxelCallback>()

        callbacksFunctions.forEach {
            val table = it.call().checktable()

            fun <C : ScriptContext, R : Any> MutableList<ScriptCallback<C, R>>.addTableCallback(name: String) {
                addIfNotNull {
                    val script = table.get(name).nullable()?.checkfunction()
                        ?.let { function -> LuaScript<C, R>(this@LuaContext, function) } ?: return@addIfNotNull null
                    ScriptCallback(listOf(script))
                }
            }

            playerInstantiate.addTableCallback("player_instantiate")
            playerDestroy.addTableCallback("player_destroy")
            worldTickSecond.addTableCallback("world_tick_second")
            worldTick.addTableCallback("world_tick")
            placeVoxel.addTableCallback("place_voxel")
        }

        return Callbacks(
            ScriptCallback(playerInstantiate.flatMap { it.scripts }),
            ScriptCallback(playerDestroy.flatMap { it.scripts }),
            ScriptCallback(worldTickSecond.flatMap { it.scripts }),
            ScriptCallback(worldTick.flatMap { it.scripts }),
            ScriptCallback(placeVoxel.flatMap { it.scripts }),
        )
    }

    fun compileContents(): CompilationResult {
        val compilation = LuaCompilationContext()
        compilationFunctions.forEach { function ->
            val namespaceTable = function.call().checktable()
            namespaceTable.get("namespaces").checktable()
                .toList { it.checktable() }
                .forEach { namespace ->
                    val namespaceId =
                        runCatching { NamespaceId(namespace.get("id").tojstring()) }.getOrNull() ?: return@forEach

                    try {
                        val itemsArray = namespace.get("items").nullable()?.checktable()
                        val scriptsArray = namespace.get("scripts").nullable()?.checktable()
                        val componentsArray = namespace.get("components").nullable()?.checktable()
                        val intentsArray = namespace.get("intents").nullable()?.checktable()

                        val items = compileItemsLua(namespaceId, itemsArray?.toList { it.checktable() } ?: emptyList())
                        val scripts = scriptsArray?.toList { it.checktable() }
                            ?.associate { script ->
                                val id = script.get("id").tojstring()
                                val function = script.get("fun").checkfunction()
                                namespacedId(namespaceId, id).toScriptId() to LuaScript<ScriptContext, Any>(this@LuaContext, function)
                            } ?: emptyMap()

                        val components = componentsArray?.toList { it.checktable() }
                            ?.associate { componentType ->
                                val componentId = namespacedId(namespaceId, componentType.get("id").tojstring()).toScriptComponentId()
                                componentId to ScriptComponentType(ComponentType(componentId.id))
                            } ?: emptyMap()

                        val intents = intentsArray?.toList { it.checktable() }
                            ?.associate { intent ->
                                val id = IntentId(namespacedId(namespaceId, intent.get("id").tojstring()))
                                val script = ScriptId(intent.get("script").tojstring())
                                val name = intent.get("name")?.nullable()?.tojstring() ?: id.value
                                val inputs = intent.get("inputs")?.nullable()?.checktable()
                                    ?.toList { it.checktable() }
                                    ?.map { it.toIntentInput() } ?: emptyList()
                                id to Intent(id, name, script, inputs)
                            } ?: emptyMap()

                        compilation.namespaces[namespaceId] = CompiledNamespace(
                            items.associateBy { it.id },
                            mapOf(),
                            mapOf(),
                            scripts,
                            components,
                            intents
                        )
                    } catch (e: Exception) {
                        compilation.errors += CompilationException(namespaceId, e)
                    }
                }
        }
        return CompilationResult(
            compilation.namespaces,
            compilation.errors,
            compileCallbacks(),
            0
        )
    }
}