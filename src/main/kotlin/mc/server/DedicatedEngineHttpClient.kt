package org.lain.engine.mc.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.lain.engine.player.account.AccountResponse
import org.lain.engine.player.account.CharacterData
import org.lain.engine.util.getJson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Duration

class DedicatedEngineHttpClient(private val uri: URI = URI.create("http://localhost:8080")) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    inner class Authorized(private val sessionTicket: String) {
        suspend fun getCharacter(id: String): CharacterData = withContext(Dispatchers.IO) {
            httpClient.getJson<CharacterData>(request("characters/$id"), sessionTicket)
        }

        suspend fun getAccount(): AccountResponse = withContext(Dispatchers.IO) {
            httpClient.getJson<AccountResponse>(request("me"), sessionTicket)
        }
    }

    private fun request(path: String): HttpRequest.Builder {
        return HttpRequest
            .newBuilder(uri.resolve(path))
            .timeout(Duration.ofSeconds(5))
    }

    private fun HttpRequest.Builder.authorization(ticket: String): HttpRequest.Builder {
        return header("Authorization", "Bearer $ticket")
    }

    fun getAuthorized(sessionTicket: String) = Authorized(sessionTicket)
}