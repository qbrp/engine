package org.lain.engine.util

import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

inline fun <reified T : Any> HttpClient.getJson(request: HttpRequest.Builder, bearerToken: String? = null): T {
    val builder = request
        .header("Accept", "application/json")
        .GET()
    if (bearerToken != null) {
        builder.header("Authorization", "Bearer $bearerToken")
    }
    return sendJson(builder.build())
}

inline fun <reified B : Any, reified T : Any> HttpClient.postJson(request: HttpRequest.Builder, body: B): T {
    return sendJson(
        request
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(Json.encodeToString(body)))
            .build()
    )
}

inline fun <reified T : Any> HttpClient.sendJson(request: HttpRequest): T {
    val response = send(request, HttpResponse.BodyHandlers.ofString())
    val responseBody = response.body()
    if (response.statusCode() !in 200..299) {
        throw HttpStatusException(response.statusCode(), request.uri(), responseBody)
    }
    return Json.decodeFromString(responseBody)
}


class HttpStatusException(
    val statusCode: Int,
    val uri: URI,
    val responseBody: String,
) : RuntimeException("HTTP $statusCode from $uri: ${responseBody.take(500)}")