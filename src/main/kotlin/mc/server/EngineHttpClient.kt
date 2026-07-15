package org.lain.engine.mc.server

import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class EngineHttpClient(val uri: URI = URI("http://localhost:8080")) {
    private val requestTimeout: Duration = Duration.ofSeconds(3)
    val rest = HttpClient
        .newBuilder()
        .connectTimeout(requestTimeout)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()!!

    inline fun <reified T : Any> getJson(path: String, bearerToken: String? = null): T {
        return sendJson(
            request(path)
                .header("Accept", "application/json")
                .apply {
                    bearerToken?.let { header("Authorization", "Bearer $bearerToken") }
                }
                .GET()
                .build()
        )
    }

    inline fun <reified B : Any, reified T : Any> postJson(path: String, body: B, bearerToken: String? = null): T {
        return sendJson(
            request(path)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.encodeToString(body)))
                .apply {
                    bearerToken?.let { header("Authorization", "Bearer $bearerToken") }
                }
                .build()
        )
    }

    inline fun <reified T : Any> sendJson(request: HttpRequest): T {
        val response = rest.send(request, HttpResponse.BodyHandlers.ofString())
        val responseBody = response.body()
        if (response.statusCode() !in 200..299) {
            throw HttpStatusException(response.statusCode(), request.uri(), responseBody)
        }
        return Json.decodeFromString(responseBody)
    }

    fun request(path: String): HttpRequest.Builder {
        return HttpRequest.newBuilder(uri.resolve(path))
            .timeout(requestTimeout)
    }
}