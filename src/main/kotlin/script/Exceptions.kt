package org.lain.engine.script

import org.lain.engine.util.Timestamp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

data class ScriptException(val script: Script<*, *>, override val cause: Throwable) : RuntimeException()

fun handleScriptException(script: Script<*, *>, cause: Throwable) = ScriptExceptionHandler.handleScriptException(ScriptException(script, cause))

object ScriptExceptionHandler {
    data class Signature(val kclass: KClass<out Throwable>, val message: String?, val stackrace: List<String>)
    data class LogState(var summaryTime: Timestamp, var count: Int)

    private val STACKTRACE_FINGERPRINT_SIZE = 7
    private val SUMMARY_LOG_TIMEOUT = 5000
    private val ERROR_LOG_TIMEOUT = 10000
    private val loggedErrors = HashMap<Signature, LogState>()
    private val lock = Any()

    fun handleScriptException(error: ScriptException) = synchronized(lock) {
        val e = error.cause
        val stacktrace = e.stackTrace
        val signature = Signature(
            e::class,
            e.message,
            stacktrace
                .take(STACKTRACE_FINGERPRINT_SIZE)
                .map { it.toString() }
        )

        var doFullLog = true
        val errorLogState = loggedErrors[signature]
        if (errorLogState != null) {
            val timeElapsed = errorLogState.summaryTime.timeElapsed()
            errorLogState.count += 1
            if (timeElapsed <= ERROR_LOG_TIMEOUT) doFullLog = false
            if (timeElapsed >= SUMMARY_LOG_TIMEOUT) {
                val errorString = e.message?.let { "Ошибка $it" } ?: "Неизвестная ошибка"
                val dateTime =
                    DateTimeFormatter.ofPattern("HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(errorLogState.summaryTime.timeMillis))
                val count = errorLogState.count
                errorLogState.summaryTime = Timestamp()
                LOGGER.error("$errorString повторилась $count раз с момента $dateTime")
            }
        }

        if (doFullLog) {
            loggedErrors[signature] = LogState(Timestamp(), 1)
            LOGGER.error("Ошибка выполнения скрипта ${error.script}", e)
        }
    }
}