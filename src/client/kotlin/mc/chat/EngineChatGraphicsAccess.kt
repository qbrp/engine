package org.lain.engine.client.mc.chat

internal interface EngineChatGraphicsAccess {
    fun `engine$addMessage`(line: EngineChatHudMessage, isVisible: Boolean)
}
