package org.lain.engine.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.getComponent
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.fixedRateTimer
import kotlin.reflect.jvm.jvmName

@Serializable
data class DebugName(val name: String) : Component

@JvmInline
value class EntityDebugId(val name: String)

fun EntityDebugNameId(name: String, id: EntityId) = EntityDebugId("$name+$id")

context(world: World)
fun EntityId.getDebugName() = getComponent<DebugName>()?.name

context(world: World)
fun EntityId.getEntityDebugNameId() = getComponent<DebugName>()?.name
    ?.let { EntityDebugNameId(it, this) }
    ?: getDebugId()

fun EntityId.getDebugId() = EntityDebugId("$this")

@Serializable
data class Log(
    val message: String,
    val level: LogLevel,
    val data: Map<String, String> = emptyMap(),
    val tick: Long? = null,
    val world: WorldId? = null,
    val error: Error? = null,
    val timestamp: Timestamp = Timestamp(),
    val thread: String = Thread.currentThread().name,
) {
    @Serializable
    data class Error(val clazz: String, val data: String, val stacktrace: List<String>)
}

fun Throwable.toLogError() =
    Log.Error(this::class.qualifiedName ?: this::class.jvmName, this.toString(), this.stackTrace.map { it.toString() })

object LogMessages {
    const val ITEM_LOAD = "Item loaded"
    const val ITEM_LOAD_ERROR = "Couldn't load item"
    const val ITEM_STACK_INIT_ERROR = "Couldn't initialize item stack"
    const val ENTITY_SYNC = "Entity synced"
    const val ENTITY_SYNC_ADD = "Synced entity added"
    const val PLAYER_INTERACTION = "Player interaction"
    const val INTERACTION_SKIP = "Skipped interaction sync"
}

enum class LogLevel {
    INFO, WARN, ERROR, FATAL
}

data class LogDiagnosticContext(val data: Map<String, String> = mapOf())

fun LogDiagnosticContext.append(diagnostic: Map<String, String>) =
    LogDiagnosticContext(diagnostic + data)

fun LogDiagnosticContext.append(vararg values: Pair<String, String>) =
    append(mapOf(*values))

object EngineLogger {
    private val messageWriteQueue: ConcurrentLinkedQueue<Log> = ConcurrentLinkedQueue()
    private val slf4jLoggerAdapter = LoggerFactory.getLogger("Engine")

    @Volatile
    private lateinit var file: File
    private var session = 0
    private val writeLock = Any()

    init {
        newSession()
        startWriter()
    }

    private fun newSessionFile() =
        File(
            "logs",
            LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + ".log"
        ).also {
            it.createNewFile()
        }

    fun newSession() = synchronized(writeLock) {
        if (::file.isInitialized) {
            writeLogs(file)
        }

        session++
        file = newSessionFile()
    }

    fun startWriter() {
        fixedRateTimer("Engine Log Writer", period = 5000L) {
            writeLogs(file)
        }
        Runtime.getRuntime().addShutdownHook(Thread { writeLogs(file) })
    }


    private fun writeLogs(file: File) = synchronized(writeLock) {
        val messagesToWrite = mutableListOf<Log>()
        messageWriteQueue.flush { messagesToWrite.add(it) }

        if (messagesToWrite.isEmpty()) return

        Files.newBufferedWriter(
            file.toPath(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        ).use { writer ->
            messagesToWrite.forEach {
                writer.appendLine(Json.encodeToString(it))
            }
        }
    }

    fun log(log: Log) {
        messageWriteQueue.add(log)
        val startStringBuilder = StringBuilder()
        if (log.world != null) {
            startStringBuilder.append("[${log.world.value}]")
        }
        if (log.tick != null) {
            startStringBuilder.append(" [${log.tick}] ")
        }
        val msg = "$startStringBuilder${log.message} (${log.data})"
        when (log.level) {
            LogLevel.INFO -> slf4jLoggerAdapter.info(msg)
            LogLevel.WARN -> slf4jLoggerAdapter.warn(msg)
            LogLevel.ERROR -> slf4jLoggerAdapter.error(msg, log.error)
            LogLevel.FATAL -> slf4jLoggerAdapter.error(msg, log.error)
        }
    }

    context(diagnosticContext: LogDiagnosticContext)
    fun logContextual(log: Log) {
        log(log.copy(data = diagnosticContext.data + log.data))
    }
}