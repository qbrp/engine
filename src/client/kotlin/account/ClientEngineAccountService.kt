package org.lain.engine.client.account

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.Util
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.server.account.EngineHttpClient
import org.lain.engine.server.account.HttpStatusException
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class ClientEngineAccountService(private val httpClient: EngineHttpClient) {
    private val logger = LoggerFactory.getLogger("Engine Backend Client")
    private var server: LocalAuthorizationServer? = null

    inner class OAuth2Authorization internal constructor(
        internal val server: LocalAuthorizationServer,
        private val codeCompletableDeferred: CompletableDeferred<String>
    ) {
        fun abort() {
            server.close()
            this@ClientEngineAccountService.server = null
            logger.info("Aborted authorization")
        }

        suspend fun await(): ClientAuthorizedAccount {
            val code = codeCompletableDeferred.await()
            val tokenResponse = exchangeCode(code)
            requestMinecraftWindowFocus()
            return ClientAuthorizedAccount(httpClient, tokenResponse)
        }
    }

    suspend fun authorizeDiscordOAuth2(): OAuth2Authorization = withContext(Dispatchers.IO) {
        val codeDeferred = CompletableDeferred<String>()
        val localServer = server ?: runCatching { LocalAuthorizationServer.start(codeDeferred) }
            .onFailure { logger.error("Failed to start local account authorization server", it) }
            .getOrThrow()
        try {
            val loginUri = buildLoginUri(localServer.redirectUri)
            logger.info("Opening Engine account authorization page: {}", loginUri)
            Util.getPlatform().openUri(loginUri)
            server = localServer
            OAuth2Authorization(localServer, codeDeferred)
        } catch (e: Throwable) {
            localServer.close()
            throw e
        }
    }

    suspend fun authorizeRefreshToken(refreshToken: String): ClientAuthorizedAccount = withContext(Dispatchers.IO) {
        try {
            val tokens = httpClient.postJson<RefreshTokenRequest, TokenResponse>(
                "/auth/refresh",
                RefreshTokenRequest(refreshToken)
            )
            ClientAuthorizedAccount(httpClient, tokens)
        } catch (e: HttpStatusException) {
            if (e.statusCode == 401 || e.statusCode == 403) {
                RefreshTokenStorage.delete()
            }
            throw e
        }
    }

    private fun exchangeCode(code: String): TokenResponse {
        return httpClient.postJson("/auth/exchange", ExchangeCodeRequest(code))
    }

    private fun buildLoginUri(redirectUri: URI): URI {
        val query = listOf(
            "client" to "game",
            "redirect_uri" to redirectUri.toString(),
        ).joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

        return httpClient.uri.resolve("auth/discord/login?$query")
    }

    private fun String.urlEncode(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8)
    }

    private fun requestMinecraftWindowFocus() {
        MinecraftClient.execute {
            val handle = MinecraftClient.window.window
            if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) {
                GLFW.glfwRestoreWindow(handle)
            }
            GLFW.glfwRequestWindowAttention(handle)
            GLFW.glfwFocusWindow(handle)
        }
    }
}

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
                    //BACKEND_LOGGER.info("Stopped local Engine account authorization server")
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

            //BACKEND_LOGGER.info("Started local Engine account authorization server at {}", redirectUri)
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
