package org.lain.engine.mc.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.net.URI
import java.time.Duration
import java.time.Instant

class DedicatedEngineAccountService {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = EngineHttpClient(URI.create("http://localhost:8080"))

    fun getAuthorized(sessionTicket: String) = AuthorizedAccount(
        httpClient,
        sessionTicket,
        Instant.now().plus(Duration.ofMillis(30L))
    )
}