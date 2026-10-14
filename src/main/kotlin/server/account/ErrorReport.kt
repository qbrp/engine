package org.lain.engine.server.account

import kotlinx.serialization.Serializable

@Serializable
data class ErrorReportPost(
    val error: ErrorReport,
    val env: ClientEnvironment,
)

@Serializable
data class ErrorReport(
    val errorClass: String,
    val errorMessage: String,
    val stackTrace: String,
    val thread: String,
    val phase: String,
)

@Serializable
data class ClientEnvironment(
    val engineVersion: String,
    val mods: List<String>,
    val multiplayer: Boolean
)

fun Exception.errorReport(phase: String) = ErrorReport(
    this::class.let { it.qualifiedName ?: it.simpleName ?: it.toString() },
    message ?: "",
    stackTraceToString(),
    Thread.currentThread().name,
    phase
)