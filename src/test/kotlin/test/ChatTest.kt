package org.lain.engine.test

import org.lain.engine.chat.MessageId
import org.lain.engine.chat.MessageSource
import org.lain.engine.chat.OutcomingMessage
import org.lain.engine.client.chat.ChatBar
import org.lain.engine.client.chat.ChatBarSection
import org.lain.engine.client.chat.ChatFormatSettings
import org.lain.engine.client.chat.LiteralSystemEngineChatMessage
import org.lain.engine.client.chat.SYSTEM_CHANNEL
import org.lain.engine.client.chat.acceptOutcomingMessage
import org.lain.engine.client.chat.isMessageVisible
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import kotlin.test.BeforeTest
import kotlin.test.Test

class ChatTest {
    private lateinit var chatBar: ChatBar

    @BeforeTest
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
            World(WorldId("dummy")),
            "Dummy message",
            isSpy = true
        )

        chatBar.toggleHide(SYSTEM_CHANNEL.id, null)

        assert(!isMessageVisible(message, true, chatBar)) { "Не должно быть видно" }
        val message2 = LiteralSystemEngineChatMessage(
            World(WorldId("dummy")),
            "Dummy message",
        )


        // Кейс #2: Обычное сообщение во включенном системном канале
        chatBar.toggleHide(SYSTEM_CHANNEL.id, null)

        assert(isMessageVisible(message2, false, chatBar)) { "Должно быть видно" }
    }

    @Test
    fun testMentions() {
        val world = World(WorldId("dummy"))
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

        assert(acceptedMessage.isMentioned)
        assert(acceptedMessage.display.contains("<bold><yellow>@$nickname</yellow></bold>"))
    }
}