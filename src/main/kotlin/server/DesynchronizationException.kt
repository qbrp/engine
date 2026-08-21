package org.lain.engine.server

class DesynchronizationException(message: String) : RuntimeException(message)

fun desync(message: String): Nothing = throw DesynchronizationException(message)