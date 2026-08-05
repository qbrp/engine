package org.lain.engine.server.account

import kotlinx.serialization.Serializable
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