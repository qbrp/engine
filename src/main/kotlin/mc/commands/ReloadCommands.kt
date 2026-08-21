package org.lain.engine.mc.commands

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.commands.CommandSourceStack
import org.lain.engine.util.file.applyConfig
import org.lain.engine.util.file.loadOrCreateServerConfig
import org.lain.engine.util.requireEngineMinecraftServer
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

fun ServerCommandDispatcher.registerEngineReloadCommands(dedicated: Boolean) {
    val server by lazy { requireEngineMinecraftServer() }
    val engine by lazy { server.engine }
    register(
        literal("re")
            .then(
                literal("config")
                    .requires { it.hasPermission("re.config") }
                    .executeCatching { ctx ->
                        try {
                            server.applyConfig(loadOrCreateServerConfig())
                            ctx.sendFeedback("Конфигурация перезагружена", true)
                        } catch (e: Exception) {
                            ctx.sendError("Возникла ошибка при применении конфигурации: ${e.message ?: "Unknown"}")
                        }
                    }
            )
            .then(
                literal("compile")
                    .requires { it.hasPermission("re.compile") }
                    .executeCatching { ctx ->
                        server.recompileEngineContents(ctx.player)
                        ctx.sendFeedback("Контент скомпилирован", true)
                    }
            )
            .then(
                literal("script")
                    .requires { it.hasPermission("re.script") }
                    .then(
                        stringArgument("path")
                            .suggests(ScriptPathSuggestionProvider())
                            .executeCatching { ctx ->
                                val path = ctx.command.getString("path")
                                    .replace(".", "/")
                                val scriptPath = "$path.lua"
                                server.luaScriptEngine.reloadScript(scriptPath)
                                engine.handler.onScriptReloaded(scriptPath)
                                ctx.sendFeedback("Скрипт по пути $scriptPath перезагружен", true)
                                ctx.sendFeedback("Используйте /re compile для применения изменений", false)
                            }
                    )
            )
    )
}

class ScriptPathSuggestionProvider : SuggestionProvider<CommandSourceStack> {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getSuggestions(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val server = requireEngineMinecraftServer() // потокобезопасно?

        val state = paths.get()
        if (state is PathsState.Outdated) {
            val version = state.version
            val computingState = PathsState.Computing(version)
            if (paths.compareAndSet(state, computingState)) {
                val scriptsDirectory = File(server.luaScriptEngine.scriptsPath)
                scope.launch {
                    runCatching {
                        scriptFilesList(scriptsDirectory)
                    }.onSuccess { scripts ->
                        if (generation.get() == version) {
                            paths.compareAndSet(
                                computingState,
                                PathsState.UpToDate(scripts)
                            )
                        }
                    }.onFailure {
                        paths.compareAndSet(
                            computingState,
                            PathsState.Outdated(generation.get())
                        )
                    }
                }
            }
        }

        val pathsList = (paths.get() as? PathsState.UpToDate)?.scripts
            ?: return builder.buildFuture()
        pathsList
            .filter { builder.remaining.isEmpty() || it.startsWith(builder.remaining) }
            .forEach { path -> builder.suggest(path) }

        return builder.buildFuture()
    }

    companion object {
        sealed class PathsState {
            data class Outdated(val version: Long) : PathsState()
            data class Computing(val version: Long) : PathsState()
            data class UpToDate(val scripts: List<String>) : PathsState()
        }
        private val paths = AtomicReference<PathsState>(PathsState.Outdated(0))
        private val generation = AtomicLong()

        fun onScriptsCompiled() {
            val version = generation.incrementAndGet()
            paths.set(PathsState.Outdated(version))
        }
    }
}

private suspend fun scriptFilesList(scriptsDirectory: File): List<String> = withContext(Dispatchers.IO) {
    scriptsDirectory
        .walkTopDown()
        .filter { it.isFile && it.extension == "lua" }
        .map {
            it
                .relativeTo(scriptsDirectory).path
                .replace("\\", "/")
                .replace(".lua", "")
                .replace("/", ".")
        }
        .toList()
}
