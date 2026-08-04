package org.lain.engine.script

import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.mc.InvalidIdException
import org.lain.engine.mc.isIdPathValid
import org.lain.engine.mc.server.SetupException
import org.lain.engine.player.interaction.ProgressionAnimation
import org.lain.engine.player.interaction.ProgressionAnimationId
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.yaml.compileContentsYaml
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerId
import org.lain.engine.util.Intent
import org.lain.engine.util.IntentId
import org.lain.engine.util.NamespaceId
import org.lain.engine.util.Timestamp
import org.lain.engine.util.component.getKotlinComponentTypeEntries
import org.lain.engine.util.file.CONFIG_LOGGER
import org.lain.engine.util.file.ENGINE_DIR
import org.lain.engine.world.SoundEvent
import org.lain.engine.world.SoundEventId
import org.lain.engine.world.World
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.to

val File.contents: File get() = this.resolve("contents")
    .also { it.mkdirs() }
val File.scripts: File get() = this.resolve("scripts")
    .also { it.mkdirs() }
val DEFAULT_NAMESPACE = NamespaceId("default")

val EngineServer.luaEntrypointDir: File
    get() = getLuaEntrypointDir(globals.serverId)

fun File.luaEntrypointDir(serverId: ServerId): File {
    return resolve("${serverId}.lua")
}
fun getLuaEntrypointDir(serverId: ServerId): File {
    return ENGINE_DIR.scripts.luaEntrypointDir(serverId)
}

internal val SCRIPT_LOGGERRR = LoggerFactory.getLogger("Script Engine")

data class CompilationException(val namespace: NamespaceId, val error: Exception) : Exception(error) {
    val errorString: String
        get() = "- $namespace: ${error.message}"

    fun log() {
        CONFIG_LOGGER.error("При компиляции пространства имён $namespace возникла ошибка", error)
    }
}

data class CompilationResult(
    val namespaces: Map<NamespaceId, CompiledNamespace>,
    val exceptions: List<CompilationException>,
    val callbacks: Callbacks?,
    val phases: List<SystemPhase>,
    val time: Long
) {
    fun log() {
        val namespaces = namespaces.values
        CONFIG_LOGGER.info(
            "Скомпилировано {} предметов, {} звуковых событий, {} прогрессий, {} компонентов и {} скриптов в пространствах имён {} за {} мл.",
            namespaces.sumOf { it.items.count() },
            namespaces.sumOf { it.sounds.count() },
            namespaces.sumOf { it.progressionAnimations.count() },
            namespaces.sumOf { it.components.count() },
            namespaces.sumOf { it.scripts.count() },
            this.namespaces.keys.joinToString(separator = ", "),
            time
        )

        if (exceptions.isNotEmpty()) {
            CONFIG_LOGGER.warn("Во время компиляции возникло ${exceptions.count()} ошибок")
            logExceptions()
        }
    }

    fun withValidatedIdentifiers(): CompilationResult {
        val exceptions = exceptions.toMutableList()
        namespaces.forEach { (id, namespace) ->
            namespace.identifiers.forEach {
                runCatching {
                    assertIdentifierValid(id, it)
                }
                    .exceptionOrNull()
                    ?.let { exceptions += it as CompilationException }
            }
        }
        return copy(exceptions = exceptions)
    }

    fun logExceptions() {
        exceptions.forEach { exception -> exception.log() }
    }
}

data class CompiledNamespace(
    val items: Map<ItemId, Item>,
    val sounds: Map<SoundEventId, SoundEvent>,
    val progressionAnimations: Map<ProgressionAnimationId, ProgressionAnimation>,
    val scripts: Map<ScriptId, Script<*, *>> = mapOf(),
    val components: Map<ScriptComponentId, ScriptComponentType> = mapOf(),
    val intents: Map<IntentId, Intent> = mapOf(),
    val systems: Map<ScriptSystemId, ScriptSystem> = mapOf()
) {
    val identifiers: List<String> get() {
        val maps = listOf(items, sounds, scripts, progressionAnimations, scripts, components, intents, systems)
        return maps.flatMap {
            it.map { (id, obj) -> id.toString() }
        }
    }

    data class Item(val prefab: ItemPrefab) {
        val id get() = prefab.id
    }

    data class ScriptSystem(
        val queryComponents: List<ScriptComponentId>,
        val side: SystemSide,
        val entityHandleScript: VoidScript<ScriptContext.SystemEntityHandle>,
    )
}

fun assertIdentifierValid(namespaceId: NamespaceId, id: String) {
    if (!isIdPathValid(id)) throw CompilationException(namespaceId, InvalidIdException(id))
}

fun NamespacedStorageAccess.loadCompilationResult(result: CompilationResult) {
    val compiledNamespaces = result.namespaces

    val namespaces = compiledNamespaces.map { (id, namespace) ->
        Namespace(
            id,
            ContentHolder(namespace.items.mapValues { it.value.prefab }),
            ContentHolder(namespace.sounds),
            ContentHolder(namespace.progressionAnimations),
            ContentHolder(namespace.scripts),
            ContentHolder(namespace.components),
            ContentHolder(namespace.intents)
        )
    }
        .associateBy { it.id }

    val components = namespaces.collect { it.components }
    val compiledSystems = compiledNamespaces.collect { it.systems }
    // Восстановление связей
    val systems = ContentHolder(
        compiledSystems.mapValues { (id, compiledSystem) ->
            ScriptSystemDefinition(
                compiledSystem.queryComponents.map {
                    components[it] ?: error("Missing component type ${it.id}")
                },
                compiledSystem.side,
                compiledSystem.entityHandleScript
            )
        }
    )

    update(
        NamespacedStorage(
            namespaces,
            namespaces.collect { it.sounds },
            namespaces.collect { it.items },
            namespaces.collect { it.progressionAnimations },
            namespaces.collect { it.scripts },
            components,
            namespaces.collect { it.intents },
            systems
        )
    )
}

fun World.registerComponentTypes(namespacesStorage: NamespacedStorageAccess) {
    val kotlinTypeEntries = getKotlinComponentTypeEntries()
    val builtinLuaTypes = CoreScriptComponents.getAll()
    val namespaceLuaTypes = namespacesStorage.components.values.toList()
    val luaTypeEntries = (namespaceLuaTypes + builtinLuaTypes).map { it to it.meta }.toList()
    componentManager.registerComponentArrays(kotlinTypeEntries + luaTypeEntries)
}

fun EngineServer.applyContentsCompileResult(result: CompilationResult) {
    result.callbacks?.let { callbacks = it }
    namespacedStorage.loadCompilationResult(result)
    listWorlds().forEach { it.registerComponentTypes(namespacedStorage) }
    scriptSystemDispatcher.load(result.phases, namespacedStorage)
    handler.onScriptsCompiled()
}

fun EngineServer.recompileContents(
    luaScriptEngine: LuaScriptEngine,
    result: CompilationResult = compileContents(ENGINE_DIR.contents, luaScriptEngine)
) {
    applyContentsCompileResult(result)
    platform.onCompiled(namespacedStorage.get())
    result.log()
}

// Функция с побочными эффектами
fun compileContents(contents: File, luaScriptEngine: LuaScriptEngine): CompilationResult {
    val start = Timestamp()
    val result1 = compileContentsYaml(contents)
    luaScriptEngine.runEntrypoint()
    val result2 = luaScriptEngine.compileContents()
    val result = CompilationResult(
        result1.namespaces + result2.namespaces,
        result1.exceptions + result2.exceptions,
        result2.callbacks,
        result1.phases + result2.phases,
        start.timeElapsed()
    ).withValidatedIdentifiers()

    return result
}