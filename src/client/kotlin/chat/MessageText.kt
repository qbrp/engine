package org.lain.engine.client.chat

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.ComponentRenderUtils
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.parseMiniMessageClient
import org.lain.engine.client.render.ui.chat.ChatState

interface MessageText {
    fun lines(width: Int): List<FormattedCharSequence>
    fun contentString(): String

    companion object {
        val FONT: Font = MinecraftClient.font
    }
}

class MiniMessageText(val text: String) : MessageText {
    private val formatted = text.parseMiniMessageClient()

    override fun lines(width: Int): List<FormattedCharSequence> = ComponentRenderUtils.wrapComponents(
        formatted,
        width,
        MessageText.FONT
    )

    override fun contentString(): String = formatted.string
}

class VanillaText(val component: Component) : MessageText {
    override fun lines(width: Int): List<FormattedCharSequence> =
        ComponentRenderUtils.wrapComponents(component, width, MessageText.FONT)

    override fun contentString(): String = component.string
}

fun ChatState.resolveText(message: AcceptedMessage) = when(allhear) {
    true -> message.undistortedDisplayText
    false -> message.displayText
}