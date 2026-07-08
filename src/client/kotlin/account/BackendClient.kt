package org.lain.engine.client.account

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.minecraft.util.Util
import org.lain.engine.client.mc.MinecraftClient
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.toKotlinDuration

private val BACKEND_LOGGER = LoggerFactory.getLogger("Engine Backend Client")

interface RefreshToken {
    fun get(): String
}

fun loadRefreshTokenFromDrive() = RefreshTokenStorage.load()

class EngineHttpClient(
    private val backendUri: URI = URI("https://engine.qbrp.fun"),
    private val requestTimeout: Duration = Duration.ofSeconds(5)
) {
    private var server: LocalAuthorizationServer? = null
    private var authorized: Authorized? = null
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(requestTimeout)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val authMutex = Mutex()

    inner class Authorized(
        val tokens: TokenResponse,
        val expireTime: Instant = Instant.now().plusSeconds(tokens.expiresIn)
    ) : RefreshToken {
        override fun get(): String = tokens.refreshToken
        init { RefreshTokenStorage.save(tokens.refreshToken) }

        suspend fun getAccountSummary(): AccountResponse = withContext(Dispatchers.IO) {
            getJson("/me", bearerToken = tokens.accessToken)
        }
    }

    inner class Authorization internal constructor(
        internal val server: LocalAuthorizationServer,
        private val codeCompletableDeferred: CompletableDeferred<String>
    ) {
        fun abort() {
            server.close()
            this@EngineHttpClient.server = null
            BACKEND_LOGGER.info("Aborted authorization")
        }

        suspend fun await(): Authorized {
            val code = codeCompletableDeferred.await()
            val tokenResponse = exchangeCode(code)
            requestMinecraftWindowFocus()
            return Authorized(tokenResponse)
        }
    }

    suspend fun authorizeDiscordOAuth2(): Authorization = withContext(Dispatchers.IO) {
        val codeDeferred = CompletableDeferred<String>()
        val localServer = server ?: runCatching { LocalAuthorizationServer.start(codeDeferred) }
            .onFailure { BACKEND_LOGGER.error("Failed to start local account authorization server", it) }
            .getOrThrow()
        try {
            val loginUri = buildLoginUri(localServer.redirectUri)
            BACKEND_LOGGER.info("Opening Engine account authorization page: {}", loginUri)
            Util.getPlatform().openUri(loginUri)
            server = localServer
            Authorization(localServer, codeDeferred)
        } catch (e: Throwable) {
            localServer.close()
            throw e
        }
    }

    suspend fun refresh(token: RefreshToken) = authMutex.withLock {
        withContext(Dispatchers.IO) {
            val returnAuthorized = authorized
                ?.let { authorized ->
                    if (authorized.expireTime.isBefore(Instant.now())) {
                        authorizeRefreshToken(authorized.tokens.refreshToken)
                    } else {
                        authorized
                    }
                }
                ?: authorizeRefreshToken(token.get())
            returnAuthorized
        }.also { authorized = it }
    }

    private suspend fun authorizeRefreshToken(refreshToken: String): Authorized = withContext(Dispatchers.IO) {
        try {
            val tokens = postJson<RefreshTokenRequest, TokenResponse>(
                "/auth/refresh",
                RefreshTokenRequest(refreshToken)
            )
            Authorized(tokens)
        } catch (e: HttpStatusException) {
            if (e.statusCode == 401 || e.statusCode == 403) {
                RefreshTokenStorage.delete()
            }
            throw e
        }
    }

    private fun exchangeCode(code: String): TokenResponse {
        return postJson("/auth/exchange", ExchangeCodeRequest(code))
    }

    private inline fun <reified T : Any> getJson(path: String, bearerToken: String? = null): T {
        val builder = request(path)
            .header("Accept", "application/json")
            .GET()
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        return sendJson(builder.build())
    }

    private inline fun <reified B : Any, reified T : Any> postJson(path: String, body: B): T {
        return sendJson(
            request(path)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.encodeToString(body)))
                .build()
        )
    }

    private inline fun <reified T : Any> sendJson(request: HttpRequest): T {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val responseBody = response.body()
        if (response.statusCode() !in 200..299) {
            throw HttpStatusException(response.statusCode(), request.uri(), responseBody)
        }
        return Json.decodeFromString(responseBody)
    }

    private fun request(path: String): HttpRequest.Builder {
        return HttpRequest.newBuilder(backendUri.resolve(path))
            .timeout(requestTimeout)
    }

    private fun buildLoginUri(redirectUri: URI): URI {
        val query = listOf(
            "client" to "game",
            "redirect_uri" to redirectUri.toString(),
        ).joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

        return backendUri.resolve("auth/discord/login?$query")
    }

    private fun String.urlEncode(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8)
    }

    private fun requestMinecraftWindowFocus() {
        MinecraftClient.execute {
            val handle = MinecraftClient.window.handle()
            if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) {
                GLFW.glfwRestoreWindow(handle)
            }
            GLFW.glfwRequestWindowAttention(handle)
            GLFW.glfwFocusWindow(handle)
        }
    }
}

class HttpStatusException(
    val statusCode: Int,
    val uri: URI,
    val responseBody: String,
) : RuntimeException("HTTP $statusCode from $uri: ${responseBody.take(500)}")

class LocalAuthorizationServer(
    private val server: HttpServer,
    val redirectUri: URI,
) : AutoCloseable {
    override fun close() {
        server.stop(0)
    }

    companion object {
        private const val CALLBACK_PATH = "/account/authorized"

        fun start(codeCompletableDeferred: CompletableDeferred<String>): LocalAuthorizationServer {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val redirectUri = URI("http://127.0.0.1:${server.address.port}$CALLBACK_PATH")
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "Engine Account Callback Server").apply { isDaemon = true }
            }

            server.executor = executor
            server.createContext(CALLBACK_PATH) { exchange ->
                val code = exchange.queryParam("code") ?: run {
                    codeCompletableDeferred.completeExceptionally(IllegalArgumentException("Missing authorization code"))
                    exchange.respondHtml(errorPage("Missing authorization code"), 400)
                    return@createContext
                }
                exchange.respondHtml(successPage())
                codeCompletableDeferred.complete(code)
                Thread {
                    Thread.sleep(500L)
                    server.stop(0)
                    executor.shutdown()
                    BACKEND_LOGGER.info("Stopped local Engine account authorization server")
                }.apply {
                    name = "Engine Account Callback Server Shutdown"
                    isDaemon = true
                    start()
                }
            }
            server.createContext("/") { exchange ->
                exchange.respondHtml(notFoundPage(), 404)
            }
            server.start()

            BACKEND_LOGGER.info("Started local Engine account authorization server at {}", redirectUri)
            return LocalAuthorizationServer(server, redirectUri)
        }

        private fun successPage(): String {
            return """
                <!doctype html>
                <html lang="ru">
                <head>
                    <meta charset="utf-8">
                    <title>Engine</title>
                </head>
                <body>
                    <p>Авторизация завершена. Возвращаем вас в Minecraft...</p>
                    <p>Если Minecraft не открылся автоматически, вернитесь в игру вручную.</p>
                </body>
                </html>
            """.trimIndent()
        }

        private fun notFoundPage(): String {
            return """
                <!doctype html>
                <html lang="ru">
                <head><meta charset="utf-8"><title>Engine Account</title></head>
                <body><p>Страница не найдена.</p></body>
                </html>
            """.trimIndent()
        }

        private fun errorPage(message: String): String {
            return """
                <!doctype html>
                <html lang="ru">
                <head><meta charset="utf-8"><title>Engine Account</title></head>
                <body><p>$message</p></body>
                </html>
            """.trimIndent()
        }

        private fun HttpExchange.respondHtml(html: String, status: Int = 200) {
            val bytes = html.toByteArray(StandardCharsets.UTF_8)
            responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            sendResponseHeaders(status, bytes.size.toLong())
            responseBody.use { it.write(bytes) }
        }
    }
}

private fun HttpExchange.queryParam(name: String): String? {
    return requestURI.toString()
        .split("&")
        .mapNotNull { part ->
            val index = part.indexOf("=")
            if (index == -1) return@mapNotNull null

            val key = URLDecoder.decode(part.substring(0, index), StandardCharsets.UTF_8)
            val value = URLDecoder.decode(part.substring(index + 1), StandardCharsets.UTF_8)

            key to value
        }
        .firstOrNull { (key, _) -> key == name }
        ?.second
}
