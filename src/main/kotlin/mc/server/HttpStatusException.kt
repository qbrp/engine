package org.lain.engine.mc.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URI

class HttpStatusException(
    val statusCode: Int,
    val uri: URI,
    val responseBody: String,
) : RuntimeException("HTTP $statusCode from $uri: ${responseBody.take(500)}") {
    fun serializeApiError(): ApiError {
        return Json.decodeFromString<ApiError>(responseBody)
    }
}

@Serializable
class ApiError(val error: String, val message: String)