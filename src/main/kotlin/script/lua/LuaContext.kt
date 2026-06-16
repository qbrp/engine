package org.lain.engine.script.lua

import org.lain.cyberia.ecs.ComponentType
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.script.*
import org.lain.engine.script.yaml.namespacedId
import org.lain.engine.util.*
import org.lain.engine.util.component.ComponentMeta
import org.lain.engine.util.file.BUILTIN_SCRIPTS_DIR
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File
import java.io.InputStream
import java.net.URL

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

fun EngineLuaGlobals() = JsePlatform.debugGlobals()

data class LuaDependencies(
    val globals: Globals,
    val namespacesStorage: NamespacedStorageAccess,
    val scriptsPath: String,
    val dataStorage: LuaDataStorage,
)

data class LuaRuntimeDependencies(
    val playerStorage: Storage<PlayerId, EnginePlayer>,
    val worlds: MutableMap<WorldId, World>
)

interface ScriptSource {
    val chunkName: String
    fun open(): InputStream
    fun exists(): Boolean
}

class FileScriptSource(val file: File): ScriptSource {
    override val chunkName: String get() = file.nameWithoutExtension

    override fun open(): InputStream {
        return file.inputStream()
    }
    override fun exists(): Boolean {
        return file.exists()
    }
}

class ResourceScriptSource(val path: String) : ScriptSource {
    private val resource: URL? = Thread.currentThread()
        .contextClassLoader
        .getResource(path)

    override val chunkName: String get() = File(path).nameWithoutExtension
    override fun open(): InputStream = resource
        ?.openStream()
        ?: error("Module not found: $path")

    override fun exists(): Boolean = resource != null
}

open class LuaContext(
    val dependencies: LuaDependencies,
    val entrypoint: ScriptSource
) {
    init { require(entrypoint.exists()) { "Входной скрипт сервера по директроии $entrypoint не найден" } }

    private var initialized = false
    var runtimeDependencies: LuaRuntimeDependencies? = null
        private set
    val globals get() = dependencies.globals
    val scriptsPath get() = dependencies.scriptsPath

    var compilationFunctions: MutableList<LuaFunction> = mutableListOf()
    val callbacksFunctions: MutableList<LuaFunction> = mutableListOf()
    val playerMetaTable: LuaTable = PlayerMetaTable()
    val worldMetaTable: LuaTable = WorldMetaTable()
    val entityMetaTable: LuaTable = EntityMetaTable()
    val logTable: LuaTable = LogTable()
    val componentTable: LuaTable = ComponentTable()
    val worldsList = LuaTable()

    fun loadWorld(world: World) {
        val worldTable = world.coerceToLua()
        worldsList[world.id.value.toLuaValue()] = worldTable
        setupWorldTableState(world, worldTable)
    }

    open fun setupTables() {
        globals.set("Player", playerMetaTable)
        globals.set("World", worldMetaTable)
        globals.set("Entity", entityMetaTable)
        globals.set("Log", logTable)
        globals.set("Component", componentTable)
    }

    open fun setupGlobalsRuntime() {
        globals.set("worlds", worldsList)
    }

    open fun setupGlobals() {}

    open fun setup(
        standardLibrary: ScriptSource = FileScriptSource(BUILTIN_SCRIPTS_DIR.resolve("core/boot.lua"))
    ) {
        if (initialized) error("Контекст Lua уже инициализирован")
        globals.setup()
        globals.get("package").get("searchers").set(4, varargsFunction { args ->
            val moduleName = args.checkjstring(1)
            val packagePath = globals.get("package")
                .get("path")
                .tojstring()
            val resourceName = moduleName.replace('.', '/')
            val templates = packagePath.split(";")

            for (template in templates) {
                val path = template.replace("?", resourceName)

                try {
                    val scriptSource = ResourceScriptSource(path)

                    scriptSource.open().use { stream ->
                        val result = globals.load(
                            stream.reader(),
                            scriptSource.chunkName
                        )

                        val func = result.arg1()

                        return@varargsFunction if (func.isfunction()) {
                            LuaValue.varargsOf(
                                func,
                                LuaValue.valueOf(scriptSource.chunkName)
                            )
                        } else {
                            LuaValue.varargsOf(
                                LuaValue.NIL,
                                LuaValue.valueOf(
                                    "'${scriptSource.chunkName}': ${result.arg(2).tojstring()}"
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }

            LuaValue.varargsOf(
                LuaValue.NIL,
                LuaValue.valueOf(
                    "\n\tno resource found for module '$moduleName'"
                )
            )
        })

        setupTables()
        require(standardLibrary.exists()) { "Скрипт загрузки стандартной библиотеки не найден" }
        // Загрузка стандартной библиотеки
        standardLibrary.open().use {
            globals.load(it.reader(), standardLibrary.chunkName).call()
        }
        setupGlobals()

        initialized = true
    }

    open fun runEntrypoint() {
        callbacksFunctions.clear()
        if (compilationFunctions.size > 1) {
            val toRemove = compilationFunctions.subList(1, compilationFunctions.size)
            compilationFunctions.removeAll(toRemove) // оставляем только функцию инициализации стандартной библиотеки
        }
        val inputStream = entrypoint.open()
        globals.load(inputStream.reader(), entrypoint.chunkName).call()
        inputStream.close()
    }

    open fun setupGame(dependencies: LuaRuntimeDependencies) {
        runtimeDependencies = dependencies
        setupGlobalsRuntime()
    }

    fun reloadScript(filename: String): LuaValue {
        val script = File(dependencies.scriptsPath).resolve(filename)
        if (!script.exists()) error("Скрипт $filename не существует")
        if (script.extension != "lua") error("Файл $filename не является скриптом")
        globals.get("package")
            .get("loaded")
            .set("test", LuaValue.NIL)
        return globals.loadfile(script.path).call()
    }

    private fun compileCallbacks(): Callbacks {
        val playerInstantiate = mutableListOf<PlayerInstantiateCallback>()
        val playerDestroy = mutableListOf<PlayerDestroyCallback>()
        val worldTickSecond = mutableListOf<WorldTickSecondCallback>()
        val worldTick = mutableListOf<WorldTickCallback>()
        val placeVoxel = mutableListOf<PlaceVoxelCallback>()
        val itemLoad = mutableListOf<ItemLoadCallback>()

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
            placeVoxel.addTableCallback("item_load")
        }

        return Callbacks(
            ScriptCallback(playerInstantiate.flatMap { it.scripts }),
            ScriptCallback(playerDestroy.flatMap { it.scripts }),
            ScriptCallback(worldTickSecond.flatMap { it.scripts }),
            ScriptCallback(worldTick.flatMap { it.scripts }),
            ScriptCallback(placeVoxel.flatMap { it.scripts }),
            ScriptCallback(itemLoad.flatMap { it.scripts }),
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
                                namespacedId(namespaceId, id).toScriptId() to LuaScript<ScriptContext, Unit>(this@LuaContext, function)
                            } ?: emptyMap()

                        val components = componentsArray?.toList { it.checktable() }
                            ?.associate { componentType ->
                                val componentId = namespacedId(namespaceId, componentType.get("id").tojstring()).toScriptComponentId()
                                val isSavable = componentType.get("savable").nullable()?.toboolean() ?: false
                                val isNetworking = componentType.get("networking").nullable()?.toboolean() ?: false
                                componentId to ScriptComponentType(
                                    ComponentType(componentId.id),
                                    ComponentMeta(isSavable, null, isNetworking)
                                )
                            } ?: emptyMap()

                        val intents = intentsArray?.toList { it.checktable() }
                            ?.associate { intent ->
                                val id = IntentId(namespacedId(namespaceId, intent.get("id").tojstring()))
                                val script = ScriptId(intent.get("script").tojstring())
                                val name = intent.get("name")?.nullable()?.tojstring() ?: id.value
                                val inputs = intent.get("inputs")?.nullable()?.checktable()
                                    ?.toList { it.checktable() }
                                    ?.map { it.toIntentInput() } ?: emptyList()
                                val permission = if(intent.get("permission")?.nullable()?.toboolean() == true) {
                                    "intent.${id.value.replace("/", ".")}"
                                } else {
                                    null
                                }
                                id to Intent(id, name, script, inputs, permission = permission)
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