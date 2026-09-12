package org.lain.engine.script.lua

import org.lain.engine.EngineSimulation
import org.lain.engine.player.EnginePlayer
import org.lain.engine.script.*
import org.lain.engine.script.compilation.Build
import org.lain.engine.script.compilation.BuildDraft
import org.lain.engine.script.compilation.CompilationAbortException
import org.lain.engine.script.compilation.CompilationContext
import org.lain.engine.script.compilation.CompilationDiagnostic
import org.lain.engine.script.compilation.CompilationDiagnosticSeverity
import org.lain.engine.script.compilation.CompilationOutcome
import org.lain.engine.script.compilation.CompilationPhase
import org.lain.engine.script.compilation.compilationManifestOf
import org.lain.engine.script.compilation.linkedNamespaces
import org.lain.engine.script.compilation.linkedSystemPhases
import org.lain.engine.script.compilation.validateNamespaces
import org.lain.engine.script.compilation.writeTo
import org.lain.engine.script.lua.compilation.LuaCompilationContext
import org.lain.engine.script.lua.compilation.CompilationContextTable
import org.lain.engine.script.lua.compilation.ReportsCollectorUserdataType
import org.lain.engine.script.lua.compilation.compiledBuildDraft
import org.lain.engine.script.lua.library.*
import org.lain.engine.script.lua.library.ecs.*
import org.lain.engine.util.Timestamp
import org.lain.engine.util.file.FileSystem
import org.lain.engine.world.World
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

open class LuaScriptEngine(
    val dependencies: Dependencies,
    val entrypoint: ScriptSource
) : ScriptEngine {
    init {
        require(entrypoint.exists()) { "Входной скрипт сервера по директроии $entrypoint не найден" }
    }

    private var initialized = false
    private var reloadFunction: LuaFunction? = null
    var runtimeDependencies: RuntimeDependencies? = null
        private set
    val globals get() = dependencies.globals
    val scriptsPath get() = dependencies.scriptsPath
    var writeCompilationManifest = dependencies.writeCompilationManifest

    val logger = LoggerTable()
    val worldsList = LuaTable()
    val idLibrary = IdLibrary()
    val vec3Library = Vec3Library()
    val componentLibrary = ComponentLibrary(dependencies.namespacesStorage)

    val playerMetaTable: LuaTable = PlayerMetaTable()
    val worldMetaTable: LuaTable = WorldMetaTable()
    val entityMetaTable: LuaTable = EntityMetaTable()
    val playerInventoryMetaTable: LuaTable = PlayerInventoryMetaTable()
    val playerModeMetaTable = PlayerModeMetaTable()
    val playerPhysicsMetaTable = PlayerPhysicsMetaTable()
    val playerInputMetaTable = PlayerInputMetaTable()
    val playerAttributesMetaTable = PlayerAttributesMetaTable()
    val playerCustomAttributesMetaTable = PlayerCustomAttributesMetaTable()
    val playerMovementStatusMetaTable = MovementStatusMetaTable()
    val playerVelocityMetaTable = PlayerVelocityMetaTable()
    val entityRpcReceiverMetaTable: LuaTable = EntityRpcReceiverMetaTable()
    val entityRpcMessageMetaTable: LuaTable = EntityRpcMessageMetaTable()
    val entityRpcQueueMetaTable: LuaTable = EntityRpcQueueMetaTable()
    val voxelMetaUserdataType = VoxelMetaUserdataType()
    val moduleFileMetaTable = ModuleFileUserdataType()
    val moduleFolderMetaTable = ModuleFolderUserdataType()
    val moduleUserdataType = ModuleUserdataType()
    val reportsCollectorUserdataType = ReportsCollectorUserdataType()

    val engineTable = luaTable {
        "SCRIPTS_PATH"(scriptsPath)
        "MODULES_PATH"(FileSystem.modules.path)
        "player"(playerMetaTable)
        "world"(worldMetaTable)
        "entity"(entityMetaTable)
        "logger"(logger)
        "vec_3"(vec3Library.library)
        "id"(idLibrary.library)
        "component"(componentLibrary.library)
        "modules"(ModulesTable(dependencies.moduleManager))
        "reports_collector"(reportsCollectorUserdataType.metaTable)
    }

    override fun loadWorld(world: World) {
        val worldTable = LuaUserdata(world).setmetatable(worldMetaTable)
        worldsList[world.id.value.luaStr()] = worldTable
    }

    open fun setupTables() {
        globals[ENGINE_TABLE] = engineTable
    }

    open fun setupGlobalsRuntime() {
        engineTable["worlds"] = worldsList
        componentLibrary.setupSimulation()
    }

    open fun mapScriptContext(context: ScriptContext): LuaValue =
        context.toLuaValue()

    open fun setup() {
        if (initialized) error("Контекст Lua уже инициализирован")
        globals.setupScriptPackageSearcher()
        setupTables()
        initialized = true
    }

    context(context: CompilationContext)
    protected fun runEntrypoint(): LuaValue {
        return try {
            val result = globals.loadScript(entrypoint).call(
                CompilationContextTable(context)
            )
            reloadFunction = engineTable["reload_script"].nullable()?.checkfunction()
            result
        } catch (e: LuaError) {
            context.exceptions.abort(
                CompilationDiagnostic(
                    severity = CompilationDiagnosticSeverity.FATAL,
                    message = e.message ?: "Неизвестная ошибка Lua",
                    phase = CompilationPhase.INSTALL
                )
            )
        }
    }

    open fun setupGame(dependencies: RuntimeDependencies) {
        runtimeDependencies = dependencies
        setupGlobalsRuntime()
    }

    override fun tickBeforeCallbacks(world: World) {
        world.applyLuaNetworkingComponents()
        world.refreshGeneralLuaComponentsView()
    }

    override fun tick(world: World) = with(world) {
        flushEntityRpcMessageReceiver()
        applyLuaLightComponents()
        applyLuaVoxelDoorComponents()
    }

    override fun setupPlayer(player: EnginePlayer) = with(player.world) {
        player.prepareLuaScriptComponents()
    }

    override fun reloadScript(moduleName: String) {
        reloadFunction?.call(moduleName.luaStr()) ?: globals.loadfile(moduleName)
    }

    override fun updateModules(modules: Modules) {
        engineTable["modules"] = ModulesTable(dependencies.moduleManager)
    }

    open fun listCallbackTypes() = CallbackType.typeList

    fun compileContents(): CompilationOutcome = with(
        LuaCompilationContext(
            callbackTypes = listCallbackTypes().toList()
        )
    ) {
        val start = Timestamp()
        var manifestBuildDraft: BuildDraft? = null
        var manifestLinkedNamespaces: Map<NamespaceId, Namespace> = emptyMap()

        val outcome = try {
            val buildDraft = compiledBuildDraft(runEntrypoint().checktable())
            manifestBuildDraft = buildDraft
            buildDraft.validateNamespaces()
            val linkedNamespaces = buildDraft.linkedNamespaces()
            manifestLinkedNamespaces = linkedNamespaces
            val linkedPhases = buildDraft.linkedSystemPhases(linkedNamespaces)

            val report = exceptions.build()
            if (report.hasErrors) {
                CompilationOutcome.Failure(report)
            } else {
                CompilationOutcome.Success(
                    Build(
                        namespaces = linkedNamespaces,
                        callbacks = Callbacks(buildDraft.callbacks),
                        phases = linkedPhases,
                        inventoryTab = buildDraft.inventoryTab,
                        time = start.timeElapsed(),
                    ),
                    report
                )
            }
        } catch (_: CompilationAbortException) {
            CompilationOutcome.Failure(exceptions.build())
        }

        writeCompilationManifest(
            outcome,
            manifestBuildDraft,
            manifestLinkedNamespaces,
        )

        outcome
    }

    private fun writeCompilationManifest(
        outcome: CompilationOutcome,
        buildDraft: BuildDraft?,
        linkedNamespaces: Map<NamespaceId, Namespace>,
    ) {
        if (!writeCompilationManifest) return

        val file = dependencies.compilationManifestFile
        runCatching {
            compilationManifestOf(
                outcome,
                buildDraft,
                dependencies.moduleManager.modules,
                linkedNamespaces,
            ).writeTo(file)
        }.onSuccess {
            ScriptEngine.LOGGER.info("Манифест компиляции сохранен в {}", file.path)
        }.onFailure { exception ->
            ScriptEngine.LOGGER.warn(
                "Не удалось сохранить манифест компиляции в ${file.path}",
                exception,
            )
        }
    }

    override fun createScriptComponent(value: ScriptValue, type: ScriptComponentType): ScriptComponent {
        return LuaScriptComponent(value.toLuaValue(), type)
    }

    data class Dependencies(
        val globals: Globals,
        val namespacesStorage: NamespacedStorageAccess,
        val dataStorage: LuaDataStorage,
        val moduleManager: ModuleManager,
        val scriptsPath: String = FileSystem.scripts.path,
        val writeCompilationManifest: Boolean = true,
        val compilationManifestFile: File =
            FileSystem.compilationManifestFile(File(scriptsPath)),
    )

    data class RuntimeDependencies(
        val simulation: EngineSimulation
    )

    companion object {
        val ENGINE_TABLE = "engine"
        fun globals(): Globals = JsePlatform.debugGlobals()
    }
}
