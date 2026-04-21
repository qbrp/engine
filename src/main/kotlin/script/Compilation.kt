package org.lain.engine.script

import net.minecraft.util.Identifier
import net.minecraft.util.InvalidIdentifierException
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.player.ProgressionAnimation
import org.lain.engine.player.ProgressionAnimationId
import org.lain.engine.script.lua.LuaContext
import org.lain.engine.script.lua.LuaRuntimeDependencies
import org.lain.engine.script.lua.writeDefaultLuaEntrypointScript
import org.lain.engine.script.yaml.compileContentsYaml
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerId
import org.lain.engine.util.Intent
import org.lain.engine.util.IntentId
import org.lain.engine.util.NamespaceId
import org.lain.engine.util.Timestamp
import org.lain.engine.util.file.CONFIG_LOGGER
import org.lain.engine.util.file.ENGINE_DIR
import org.lain.engine.world.SoundEvent
import org.lain.engine.world.SoundEventId
import org.lain.engine.world.World
import org.slf4j.LoggerFactory
import java.io.File

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

internal val LOGGER = LoggerFactory.getLogger("Script Engine")

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
        }
    }

    fun withValidateIdentifiers(): CompilationResult {
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
    val intents: Map<IntentId, Intent> = mapOf()
) {
    val identifiers: List<String> get() {
        val maps = listOf(items, sounds, scripts, progressionAnimations, scripts, components, intents)
        return maps.flatMap {
            it.map { (id, obj) -> id.toString() }
        }
    }

    data class Item(val prefab: ItemPrefab) {
        val id get() = prefab.id
    }
}

fun assertIdentifierValid(namespaceId: NamespaceId, id: String) {
    if (!Identifier.isPathValid(id)) throw CompilationException(
        namespaceId,
        InvalidIdentifierException("Non [a-z0-9/._-] character in path of location: $id")
    )
}

fun NamespacedStorage.loadContentsCompileResult(result: CompilationResult) {
    upload(
        result.namespaces.map { (id, namespace) ->
            Namespace(
                id,
                Namespace.Holder(namespace.items.mapValues { it.value.prefab }),
                Namespace.Holder(namespace.sounds),
                Namespace.Holder(namespace.progressionAnimations),
                Namespace.Holder(namespace.scripts),
                Namespace.Holder(namespace.components),
                Namespace.Holder(namespace.intents),
            )
        }
    )
}

fun World.registerScriptComponents(namespacesStorage: NamespacedStorage) {
    registerScriptComponents(namespacesStorage.components.values.toList() + BuiltinScriptComponents.ALL.values)
}

fun EngineServer.applyContentsCompileResult(result: CompilationResult) {
    result.callbacks?.let { callbacks = it }
    namespacedStorage.loadContentsCompileResult(result)
    listWorlds().forEach { it.registerScriptComponents(namespacedStorage) }
    handler.onContentsUpdate()
}

fun EngineServer.loadContents(
    luaContext: LuaContext,
    result: CompilationResult = compileContents(ENGINE_DIR.contents, luaEntrypointDir, luaContext)
) {
    applyContentsCompileResult(result)
    luaContext.setupGame(LuaRuntimeDependencies(playerStorage, worlds))
    eventListener.onCompiled(namespacedStorage)
    result.log()
}

// Функция с побочными эффектами
fun compileContents(contents: File, entrypointScript: File, luaContext: LuaContext): CompilationResult {
    val start = Timestamp()
    val result1 = compileContentsYaml(contents)
    if (!entrypointScript.exists()) {
        entrypointScript.createNewFile()
        entrypointScript.writeDefaultLuaEntrypointScript()
    }
    luaContext.setup(entrypointScript)
    val result2 = luaContext.compileContents()
    val result = CompilationResult(
        result1.namespaces + result2.namespaces,
        result1.exceptions + result2.exceptions,
        result2.callbacks,
        start.timeElapsed()
    ).withValidateIdentifiers()

    return result
}