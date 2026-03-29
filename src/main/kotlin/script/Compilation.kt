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
import java.io.File

val File.contents: File get() = this.resolve("contents")
val File.scripts: File get() = this.resolve("scripts")
val DEFAULT_NAMESPACE = NamespaceId("default")

val EngineServer.luaEntrypointDir: File
    get() = ENGINE_DIR.scripts.resolve("${globals.serverId}.lua")

data class CompilationResult(val namespaces: Map<NamespaceId, CompiledNamespace>)

data class CompiledNamespace(
    val items: Map<ItemId, Item>,
    val sounds: Map<SoundEventId, SoundEvent>,
    val progressionAnimations: Map<ProgressionAnimationId, ProgressionAnimation>
) {
    data class Item(val prefab: ItemPrefab) {
        val id get() = prefab.id
    }
}

fun NamespacedStorage.loadContentsCompileResult(result: CompilationResult) {
    upload(
        result.namespaces.map { (id, namespace) ->
            Namespace(
                id,
                Namespace.Holder(namespace.items.mapValues { it.value.prefab }),
                Namespace.Holder(namespace.sounds),
                Namespace.Holder(namespace.progressionAnimations)
            )
        }
    )
}

fun EngineServer.applyContentsCompileResult(result: CompilationResult) {
    namespacedStorage.loadContentsCompileResult(result)
    handler.onContentsUpdate()
}

fun EngineServer.loadContents(luaContext: LuaContext) {
    val results = compileContents(ENGINE_DIR, luaContext)
    applyContentsCompileResult(results)
}

fun compileContents(directory: File, luaContext: LuaContext): CompilationResult {
    val start = Timestamp()
    val result1 = compileContentsYaml(directory.contents)
    val result2 = luaContext.executeCompilation()
    val result = CompilationResult(result1.namespaces + result2.namespaces)
    val namespaces = result.namespaces.values
    val end = start.timeElapsed()

    CONFIG_LOGGER.info(
        "Скомпилировано {} предметов, {} звуковых событий и {} прогрессий в пространствах имён {} за {} мл.",
        namespaces.sumOf { it.items.count() },
        namespaces.sumOf { it.sounds.count() },
        namespaces.sumOf { it.progressionAnimations.count() },
        result.namespaces.keys.joinToString(separator = ", "),
        end
    )

    return CompilationResult(result.namespaces)
}