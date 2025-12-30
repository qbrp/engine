package org.lain.engine.client.handler

import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.handler.ClientHandler.Companion.LOGGER
import org.lain.engine.client.isLowDetailed
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.transport.ClientContext
import org.lain.engine.client.transport.ClientPacketHandler
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.mc.DisconnectText
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import java.util.Queue

class TaskExecutor(
    private val tasks: ArrayDeque<Task> = ArrayDeque()
) {
    fun flush(client: EngineClient) {
        while (tasks.isNotEmpty()) {
            val task = tasks.removeFirst()
            try {
                task.executor()
            } catch (e: Throwable) {
                ClientHandler.LOGGER.error("При обработке задачи $task возникла ошибка", e)
                if (!client.developerMode) {
                    MinecraftClient.disconnect(DisconnectText(e.message ?: "Unknown error"))
                    break
                }
            }
        }
    }

    fun add(task: Task) {
        tasks += task
    }

    fun add(name: String, executor: () -> Unit) {
        add(Task(name, executor))
    }
}

data class Task(
    val name: String,
    val executor: () -> Unit
) {
    override fun toString(): String {
        return name
    }
}

fun <P : Packet> ClientHandler.registerGameSessionReceiver(endpoint: Endpoint<P>, executor: P.(GameSession) -> Unit) {
    endpoint.registerClientReceiver {
        val session = client.gameSession ?: error("Получен пакет данных в раннем состоянии авторизации на сервере")
        taskExecutor.add(endpoint.identifier) { this.executor(session) }
    }
}

fun ClientHandler.updatePlayer(id: PlayerId, update: (Player) -> Unit) {
    val player = client.gameSession?.getPlayer(id) ?: run {
        LOGGER.warn("Получен пакет данных несуществующего игрока: $id")
        return
    }
    update(player)
}

fun ClientHandler.updatePlayerDetailed(id: PlayerId, update: (Player) -> Unit) {
    updatePlayer(id) {
        if (it.isLowDetailed) {
            LOGGER.warn("Получен пакет данных игрока вне зоны обновлений: $id")
        }
        update(it)
    }
}