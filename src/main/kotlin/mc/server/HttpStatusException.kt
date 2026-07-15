package org.lain.engine.mc.server

import java.net.URI

class HttpStatusException(
    val statusCode: Int,
    val uri: URI,
    val responseBody: String,
) : RuntimeException("HTTP $statusCode from $uri: ${responseBody.take(500)}")