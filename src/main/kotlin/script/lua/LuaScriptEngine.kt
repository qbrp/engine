package org.lain.engine.script.lua

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.script.*
import org.lain.engine.script.lua.library.*
import org.lain.engine.util.*
import org.lain.engine.util.file.BUILTIN_SCRIPTS_DIR
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.ZeroArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

open class LuaScriptEngine(
    val dependencies: Dependencies,
    val entrypoint: ScriptSource
) : ScriptEngine {
    init { require(entrypoint.exists()) { "Входной скрипт сервера по директроии $entrypoint не найден" } }

    private var initialized = false
    var runtimeDependencies: RuntimeDependencies? = null
        private set
    val globals get() = dependencies.globals
    val scriptsPath get() = dependencies.scriptsPath

    val registrationLibrary = RegistrationLibrary()
    val playerMetaTable: LuaTable = PlayerMetaTable()
    val worldMetaTable: LuaTable = WorldMetaTable()
    val entityMetaTable: LuaTable = EntityMetaTable()
    val logTable: LuaTable = LogTable()
    val componentTable: LuaTable = ComponentTable()
    val worldsList = LuaTable()

    fun loadWorld(world: World) {
        val worldTable = world.coerceToLua()
        worldsList[world.id.value.luaStr()] = worldTable
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

    open fun mapScriptContext(context: ScriptContext): LuaValue = context.toLuaValue()

    open fun setupGlobals() {}

    open fun setup(
        standardLibrary: ScriptSource = FileScriptSource(BUILTIN_SCRIPTS_DIR.resolve("core/boot.lua"))
    ) {
        if (initialized) error("Контекст Lua уже инициализирован")
        globals.set("SCRIPTS_PATH", scriptsPath)
        globals.set("LIBRARY_PATH", BUILTIN_SCRIPTS_DIR.path)
        globals.setupScriptPackageSearcher()
        registrationLibrary.setup(globals)
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
        val inputStream = entrypoint.open()
        globals.load(inputStream.reader(), entrypoint.chunkName).call()
        inputStream.close()
    }

    open fun setupGame(dependencies: RuntimeDependencies) {
        runtimeDependencies = dependencies
        setupGlobalsRuntime()
    }

    override fun reloadScript(filename: String) {
        val script = File(dependencies.scriptsPath).resolve(filename)
        if (!script.exists()) error("Скрипт $filename не существует")
        if (script.extension != "lua") error("Файл $filename не является скриптом")
        globals.loadfile(script.path).call()
    }

    open fun listCallbackTypes() = CallbackType.list()

    fun compileContents(): CompilationResult = registrationLibrary.runFunctions(listCallbackTypes())

    override fun createScriptComponent(value: ScriptValue, type: ScriptComponentType): ScriptComponent {
        return LuaScriptComponent(value.toLuaValue(), type)
    }

    data class CompilationContext(
        val namespaces: MutableMap<NamespaceId, CompiledNamespace> = mutableMapOf(),
        val errors: MutableList<CompilationException> = mutableListOf(),
    )

    data class Dependencies(
        val globals: Globals,
        val namespacesStorage: NamespacedStorageAccess,
        val scriptsPath: String,
        val dataStorage: LuaDataStorage,
    )

    data class RuntimeDependencies(
        val playerStorage: Storage<PlayerId, EnginePlayer>,
        val worlds: MutableMap<WorldId, World>
    )

    companion object {
        fun globals(): Globals = JsePlatform.debugGlobals()
    }
}
