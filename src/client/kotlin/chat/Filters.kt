package org.lain.engine.client.chat

data class MessageFilter(
    var enabled: Boolean = false,
    val supplier: (AcceptedMessage) -> Boolean,
) {
    fun matches(message: AcceptedMessage): Boolean = !enabled || supplier(message)
}

fun List<MessageFilter>.matches(message: AcceptedMessage): Boolean {
    return all { it.matches(message) }
}