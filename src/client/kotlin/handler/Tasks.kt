package org.lain.engine.client.handler

import org.lain.engine.client.handler.ClientHandler.Companion.LOGGER
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor

class TaskExecutor(
    private val tasks: ConcurrentLinkedQueue<Task> = ConcurrentLinkedQueue()
) : Executor {
    override fun execute(command: Runnable) {
        tasks.add(Task("Unnamed command") { command.run() })
    }

    fun flush() {
        while (tasks.isNotEmpty()) {
            val task = tasks.poll() ?: continue
            task.executor()
        }
    }

    fun clear() {
        tasks.clear()
    }

    fun notEmpty() = tasks.isNotEmpty()

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

class BufferedTaskQueue {
    private val lock = Any()
    private val pending = ArrayDeque<Task>()
    private var executor: TaskExecutor? = null
    private var closed = false

    fun add(task: Task) {
        synchronized(lock) {
            if (closed) return
            val executor = executor
            if (executor == null) {
                pending.addLast(task)
            } else {
                executor.add(task)
            }
        }
    }

    fun bind(executor: TaskExecutor) {
        synchronized(lock) {
            check(!closed) { "Очередь задач закрыта" }
            check(this.executor == null) { "Исполнитель задач уже привязан" }
            // Новые пакеты не должны обогнать накопленные при передаче очереди в DataPrepare.
            pending.forEach(executor::add)
            pending.clear()
            this.executor = executor
        }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            pending.clear()
            executor?.clear()
            executor = null
        }
    }
}

fun ClientHandler.updatePlayer(id: PlayerId, update: (EnginePlayer) -> Unit) {
    val player = gameSession.getPlayer(id) ?: run {
        LOGGER.warn("Получен пакет данных несуществующего игрока: $id")
        return
    }
    update(player)
}
