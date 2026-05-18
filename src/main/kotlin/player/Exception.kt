package org.lain.engine.player

import org.slf4j.Logger

class PlayerTickException(val player: EnginePlayer, override val cause: Throwable) : RuntimeException() {
    override val message: String?
        get() = cause.message

    val information = mapOf(
        "Id" to player.id.value.toString(),
        "Name" to player.username,
        "Display Name" to player.displayName,
        "Components" to player.getComponents().joinToString(",") { it.toString() },
    )

    fun log(logger: Logger) {
        logger.error("Ошибка обновления игрока", this)
        logger.error("Информация: ${information.toList().joinToString("\n") { (k, v) -> "$k: $v" }}")
    }
}