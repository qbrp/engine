package org.lain.engine.mc.commands

import org.lain.engine.util.file.applyConfig
import org.lain.engine.util.file.loadOrCreateServerConfig
import org.lain.engine.util.injectMinecraftEngineServer

fun ServerCommandDispatcher.registerEngineReloadCommands(dedicated: Boolean) {
    val server by injectMinecraftEngineServer()
    val playerTable by lazy { server.entityTable }
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
    )
}