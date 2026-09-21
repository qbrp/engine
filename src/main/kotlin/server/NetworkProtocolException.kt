package org.lain.engine.server

class NetworkProtocolException(message: String) : RuntimeException(message)

fun protocolError(message: String): Nothing = throw NetworkProtocolException(message)