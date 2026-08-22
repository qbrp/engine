package org.lain.engine.test

import org.lain.engine.chat.MessageId
import org.lain.engine.chat.MessageSource
import org.lain.engine.chat.OutcomingMessage
import org.lain.engine.client.chat.*
import org.lain.engine.client.mc.chat.DummyWorld
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChatTest : EngineTest() {
    private lateinit var chatBar: ChatBar

    @BeforeEach
    fun setup() {
        chatBar = ChatBar(
            listOf(
                ChatBarSection(
                    listOf(SYSTEM_CHANNEL.id),
                    "System"
                )
            )
        )
    }

    @Test
    fun testMessageFiltering() {
        // Кейс #1: Подслушанное сообщение в отключенном системном канале
        val message = LiteralSystemEngineChatMessage(
            DummyWorld(),
            "Dummy message",
            isSpy = true
        )

        chatBar.toggleHide(SYSTEM_CHANNEL.id, null)

        assertFalse(isMessageVisible(message, true, chatBar), "Не должно быть видно")
        val message2 = LiteralSystemEngineChatMessage(
            DummyWorld(),
            "Dummy message",
        )

        // Кейс #2: Обычное сообщение во включенном системном канале
        chatBar.toggleHide(SYSTEM_CHANNEL.id, null)

        assertTrue(isMessageVisible(message2, false, chatBar), "Должно быть видно")
    }

    @Test
    fun testMentions() {
        val world = DummyWorld()
        val nickname = "Menti123on"
        val message = OutcomingMessage(
            "Test message with @$nickname<>",
            MessageSource.getSystem(world),
            SYSTEM_CHANNEL.id,
            true,
            id = MessageId.next()
        )
        val acceptedMessage = acceptOutcomingMessage(
            message,
            mapOf(SYSTEM_CHANNEL.id to SYSTEM_CHANNEL),
            SYSTEM_CHANNEL,
            emptyMap(),
            ChatFormatSettings(),
            listOf(nickname)
        )

        assertTrue(acceptedMessage.isMentioned)
        assertTrue(acceptedMessage.text.contains("<bold><yellow>@$nickname</yellow></bold>"))
    }
}
