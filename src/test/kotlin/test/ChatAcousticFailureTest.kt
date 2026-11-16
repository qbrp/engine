package org.lain.engine.test

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.chat.Acoustic
import org.lain.engine.chat.AcousticFormatting
import org.lain.engine.chat.AcousticLevel
import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.ChatChannel
import org.lain.engine.chat.EngineChat
import org.lain.engine.chat.EngineChatSettings
import org.lain.engine.chat.MessageAuthor
import org.lain.engine.chat.MessageSource
import org.lain.engine.chat.acoustic.AcousticSimulationResult
import org.lain.engine.chat.acoustic.AcousticSimulator
import org.lain.engine.mc.InvalidMessageSourcePositionException
import org.lain.engine.player.AcousticMessageQueue
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.randomPlayerId
import org.lain.engine.player.require
import org.lain.engine.server.EngineServer
import org.lain.engine.server.Notification
import org.lain.engine.server.ServerHandler
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.packet.PlayerNotificationPacket
import org.lain.engine.util.Injector
import org.lain.engine.util.Timestamp
import org.lain.engine.util.math.ImmutableEVec3
import org.lain.engine.util.math.Pos
import org.lain.engine.world.WorldId
import java.nio.file.Path
import java.util.LinkedList

class ChatAcousticFailureTest : EngineTest() {
    @field:TempDir
    lateinit var temporaryDirectory: Path

    private lateinit var server: EngineServer
    private lateinit var chat: EngineChat
    private lateinit var transport: TestServerTransportContext
    private var previousTransport: ServerTransportContext? = null
    private val simulator = TestAcousticSimulator()
    private val channel = ChatChannel(
        ChannelId("test"), "Test", "{text}", acoustic = Acoustic.Realistic(distort = false), speech = true,
    )

    @BeforeEach
    fun setup() {
        previousTransport = Injector.resolveOrNull(ServerTransportContext::class)
        server = setupTestEngineServer(temporaryDirectory)
        transport = Injector.resolve<ServerTransportContext>() as TestServerTransportContext
        chat = EngineChat(simulator, server)
        chat.onSettingsUpdated(
            EngineChatSettings(
                realisticAcousticFormatting = AcousticFormatting(
                    listOf(AcousticLevel(0f, inputPlaceholders = emptyMap(), outPlaceholders = emptyMap()))
                )
            )
        )
    }

    @AfterEach
    fun cleanup() = runBlocking {
        chat.close()
        server.chat.close()
        server.playerPersistence.close()
        server.chunkPersistence.close()
        server.characterManager.close()
        previousTransport?.let { Injector.register<ServerTransportContext>(it) }
            ?: Injector.unregister<ServerTransportContext>()
    }

    @Test
    fun simulationErrorsNotifyTheAuthorAndDoNotDeliverToNearbyPlayers() = runBlocking {
        val author = addPlayer("author")
        val listener = addPlayer("listener")
        val source = source(author.first, listener.first)

        for ((failure, notification) in listOf(
            InvalidMessageSourcePositionException(500) to Notification.INVALID_SOURCE_POS,
            IllegalStateException("scene unavailable") to Notification.ACOUSTIC_ERROR,
        )) {
            simulator.simulate = { throw failure }
            with(chat) { channel.processMessage("hello", 0.5f, source, false) }
            server.update()

            val sent = transport.sentPackets.map { it.packet }.filterIsInstance<PlayerNotificationPacket>()
            assertEquals(listOf(PlayerNotificationPacket(notification, true)), sent)
            assertEquals(1, author.second.require<AcousticMessageQueue>().messages.size)
            assertTrue(listener.second.require<AcousticMessageQueue>().messages.isEmpty())
            author.second.require<AcousticMessageQueue>().messages.clear()
            transport.sentPackets.clear()
        }
    }

    @Test
    fun cancellationPropagatesInsteadOfBecomingAnAcousticError() {
        val author = addPlayer("author")
        val cancellation = CancellationException("cancelled")
        simulator.simulate = { throw cancellation }

        val thrown = assertThrows<CancellationException> {
            runBlocking { with(chat) { channel.processMessage("hello", 0.5f, source(author.first), false) } }
        }

        assertSame(cancellation, thrown)
        server.update()
        assertTrue(transport.sentPackets.none { it.packet is PlayerNotificationPacket })
    }

    @Test
    fun acquiredResultIsFinishedWhenReadingVolumesFails() {
        val listener = addPlayer("listener")
        var finished = 0
        val failure = IllegalStateException("cannot read volume")
        simulator.simulate = {
            object : AcousticSimulationResult {
                override fun debug(player: EnginePlayer, handler: ServerHandler, radius: Float) = Unit
                override fun getVolume(pos: Pos): Float? = throw failure
                override fun finish() { finished++ }
            }
        }

        val thrown = assertThrows<IllegalStateException> {
            runBlocking { with(chat) { channel.processMessage("hello", 0.5f, source(null, listener.first), false) } }
        }

        assertSame(failure, thrown)
        assertEquals(1, finished)
    }

    @Test
    fun acquiredResultRemainsAvailableUntilAfterVolumeReads() = runBlocking {
        val listener = addPlayer("listener")
        var finished = false
        var reads = 0
        simulator.simulate = {
            object : AcousticSimulationResult {
                override fun debug(player: EnginePlayer, handler: ServerHandler, radius: Float) = Unit
                override fun getVolume(pos: Pos): Float {
                    assertFalse(finished)
                    reads++
                    return 0.4f
                }
                override fun finish() { finished = true }
            }
        }

        with(chat) { channel.processMessage("hello", 0.5f, source(null, listener.first), false) }
        server.update()

        assertTrue(reads > 0)
        assertTrue(finished)
        assertEquals(1, listener.second.require<AcousticMessageQueue>().messages.size)
    }

    private fun addPlayer(name: String): Pair<MessageSource.Player, EnginePlayer> {
        val id = randomPlayerId()
        val world = server.simulation.defaultWorld
        val entity = world.addEntity()
        with(world) { entity.setComponent(AcousticMessageQueue(LinkedList())) }
        val player = EnginePlayer(id, entity, world)
        server.playerStorage.add(player)
        return snapshot(id, name) to player
    }

    private fun snapshot(id: PlayerId, name: String) = MessageSource.Player(
        id = id,
        pos = ImmutableEVec3(),
        isSpectating = false,
        chatHeadsEnabled = true,
        eyePos = ImmutableEVec3(),
        displayName = name,
        displayNameMiniMessage = name,
        userName = name,
        readPermission = true,
        writePermission = true,
        chatOperator = false,
    )

    private fun source(author: MessageSource.Player?, vararg otherPlayers: MessageSource.Player): MessageSource {
        val players = (listOfNotNull(author) + otherPlayers).associateBy { it.id }
        return MessageSource(
            MessageSource.World(server.simulation.defaultWorld.id, players),
            MessageAuthor(author?.displayName ?: "source", author),
            Timestamp(),
            ImmutableEVec3(),
        )
    }

    private class TestAcousticSimulator : AcousticSimulator {
        var simulate: suspend () -> AcousticSimulationResult = { AcousticSimulationResult.DUMMY }

        override suspend fun simulateSingleSource(
            world: WorldId, pos: Pos, volume: Float, maxVolume: Float, attenuation: Float,
        ): AcousticSimulationResult = simulate()
    }
}
