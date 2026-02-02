package org.lain.engine.client.handler

import org.lain.engine.client.GameSession
import org.lain.engine.client.handler.ClientHandler.Companion.LOGGER
import org.lain.engine.client.transport.isLowDetailed
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet

class TaskExecutor(
    private val tasks: ArrayDeque<Task> = ArrayDeque()
) {
    fun flush() {
        while (tasks.isNotEmpty()) {
            val task = tasks.removeFirst()
            task.executor()
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
        val session = client.gameSession ?: error("Получен пакет данных в раннем состоянии авторизации на сервере [${endpoint.identifier}]")
        taskExecutor.add(endpoint.identifier) { this.executor(session) }
    }
}

fun ClientHandler.updatePlayer(id: PlayerId, update: (EnginePlayer) -> Unit) {
    val player = client.gameSession?.getPlayer(id) ?: run {
        LOGGER.warn("Получен пакет данных несуществующего игрока: $id")
        return
    }
    update(player)
}

fun ClientHandler.updatePlayerDetailed(id: PlayerId, update: (EnginePlayer) -> Unit) {
    updatePlayer(id) {
        if (it.isLowDetailed) {
            LOGGER.warn("Получен пакет данных игрока вне зоны обновлений: $it")
        }
        update(it)
    }
}