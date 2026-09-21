package org.lain.engine.client.mc.chat

internal interface EngineChatHudAccess {
    fun `engine$addMessage`(line: EngineChatHudMessage, isVisible: Boolean)
    fun `engine$selectMessage`(mouseX: Double, mouseY: Double): Boolean
}
