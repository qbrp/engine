package org.lain.engine.script

import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.player.ProgressionAnimation
import org.lain.engine.player.ProgressionAnimationId
import org.lain.engine.server.EngineServer
import org.lain.engine.util.Namespace
import org.lain.engine.util.NamespaceId
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.util.Timestamp
import org.lain.engine.util.file.CONFIG_LOGGER
import org.lain.engine.util.file.ENGINE_DIR
import org.lain.engine.world.SoundEvent
import org.lain.engine.world.SoundEventId
import org.slf4j.LoggerFactory
import java.io.File

val File.contents: File get() = this.resolve("contents")
val File.scripts: File get() = this.resolve("scripts")
val DEFAULT_NAMESPACE = NamespaceId("default")

val EngineServer.luaEntrypointDir: File
    get() = ENGINE_DIR.scripts.resolve("${globals.serverId}.lua")

internal val LOGGER = LoggerFactory.getLogger("Script Engine")

data class CompilationResult(
    val namespaces: Map<NamespaceId, CompiledNamespace>,
    val exceptions: List<Throwable> = emptyList(),
)

data class CompiledNamespace(
    val items: Map<ItemId, Item>,
    val sounds: Map<SoundEventId, SoundEvent>,
    val progressionAnimations: Map<ProgressionAnimationId, ProgressionAnimation>,
    val scripts: Map<ScriptId, Script<*, *>>
) {
    data class Item(val prefab: ItemPrefab) {
        val id get() = prefab.id
    }
}

fun logNamespaceCompilationError(namespace: NamespaceId, error: Throwable) {
    CONFIG_LOGGER.error("При компиляции пространства имён $namespace возникла ошибка", error)
}

fun NamespacedStorage.loadContentsCompileResult(result: CompilationResult) {
    upload(
        result.namespaces.map { (id, namespace) ->
            Namespace(
                id,
                Namespace.Holder(namespace.items.mapValues { it.value.prefab }),
                Namespace.Holder(namespace.sounds),
                Namespace.Holder(namespace.progressionAnimations),
                Namespace.Holder(namespace.scripts)
            )
        }
    )
}

fun EngineServer.applyContentsCompileResult(result: CompilationResult) {
    namespacedStorage.loadContentsCompileResult(result)
    handler.onContentsUpdate()
}

fun EngineServer.loadContents(luaContext: LuaContext) {
    val results = compileContents(ENGINE_DIR.contents, luaContext)
    callbacks = luaContext.compileCallbacks()
    applyContentsCompileResult(results)
}

fun compileContents(contents: File, luaContext: LuaContext): CompilationResult {
    val start = Timestamp()
    val result1 = compileContentsYaml(contents)
    val result2 = luaContext.compileContents()
    val result = CompilationResult(
        result1.namespaces + result2.namespaces,
        result1.exceptions + result2.exceptions
    )
    val namespaces = result.namespaces.values
    val exceptions = result.exceptions
    val end = start.timeElapsed()

    CONFIG_LOGGER.info(
        "Скомпилировано {} предметов, {} звуковых событий и {} прогрессий в пространствах имён {} за {} мл.",
        namespaces.sumOf { it.items.count() },
        namespaces.sumOf { it.sounds.count() },
        namespaces.sumOf { it.progressionAnimations.count() },
        result.namespaces.keys.joinToString(separator = ", "),
        end
    )

    if (exceptions.isNotEmpty()) {
        CONFIG_LOGGER.warn("Во время компиляции возникло ${exceptions.count()} ошибок")
    }

    return result
}