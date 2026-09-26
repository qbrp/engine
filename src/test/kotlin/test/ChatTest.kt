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
import org.junit.jupiter.api.io.TempDir
import org.lain.engine.chat.Acoustic
import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.ChatChannel
import org.lain.engine.chat.EngineChat
import org.lain.engine.chat.EngineChatSettings
import org.lain.engine.chat.IncomingMessage
import org.lain.engine.chat.Modifier
import org.lain.engine.chat.acoustic.AcousticSimulator
import org.lain.engine.player.PlayerId
import org.lain.engine.server.EngineServer
import org.lain.engine.world.WorldId
import java.nio.file.Path
import java.util.UUID

class ChatTest : EngineTest() {
    private lateinit var chatBar: ChatBar
    @field:TempDir
    lateinit var tempDir: Path
    private lateinit var server: EngineServer

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
        val message = LiteralSystemMessage(
            DummyWorld(),
            "Dummy message",
            isSpy = true
        )

        chatBar.toggleHide(SYSTEM_CHANNEL.id, null)

        assertFalse(isMessageVisible(message, true, chatBar), "Не должно быть видно")
        val message2 = LiteralSystemMessage(
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

//    @Test
//    fun testSpectatorChannel() {
//        server = setupTestEngineServer(tempDir)
//        val chat = EngineChat(AcousticSimulator.DUMMY, server)
//        val channelId = ChannelId("default")
//        chat.onSettingsUpdated(
//            EngineChatSettings(
//                channels = listOf(
//                    ChatChannel(
//                        id = channelId,
//                        name = "default",
//                        format = "text",
//                        modifiers = listOf(Modifier.Spectator),
//                        acoustic = Acoustic.Global
//                    )
//                )
//            )
//        )
//        chat.processMessage(
//            IncomingMessage(
//                "Test",
//                1f,
//                channelId,
//                MessageSource(
//                    MessageSource.World(
//                        server.simulation.defaultWorld.id,
//                        players = mapOf(
//                            PlayerId(UUID.randomUUID())
//                        )
//                    )
//                )
//            )
//        )
//    }
}
