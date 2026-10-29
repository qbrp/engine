package org.lain.engine.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.data.EntityPersistenceData
import org.lain.engine.data.MaterializedComponents
import org.lain.engine.data.PlayCharacterData
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.TransactionContext
import org.lain.engine.data.TransactionState
import org.lain.engine.data.createTransactionContext
import org.lain.engine.data.databasePersistentId
import org.lain.engine.data.disconnectDatabase
import org.lain.engine.data.persistentId
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Equipment
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.PlayerLoadSettings
import org.lain.engine.player.PlayerMode
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.BiologicalCategory
import org.lain.engine.player.character.BiologicalSex
import org.lain.engine.player.character.BodyType
import org.lain.engine.player.character.CharacterAppearance
import org.lain.engine.player.character.CharacterApplyEvent
import org.lain.engine.player.character.CharacterData
import org.lain.engine.player.character.CharacterDisplay
import org.lain.engine.player.character.CharacterGradientName
import org.lain.engine.player.character.CharacterHeight
import org.lain.engine.player.character.CharacterOccupiedException
import org.lain.engine.player.character.CharacterPhysical
import org.lain.engine.player.character.CharacterProfile
import org.lain.engine.player.character.CharacterSource
import org.lain.engine.player.character.EngineBackend
import org.lain.engine.player.character.Look
import org.lain.engine.player.character.OwnedCharacterId
import org.lain.engine.player.character.SelectedLook
import org.lain.engine.player.character.ServerCharacterData
import org.lain.engine.player.character.ServerCharacterId
import org.lain.engine.player.character.ServerboundSelectedCharacter
import org.lain.engine.player.character.Skin
import org.lain.engine.player.character.UsedCharacters
import org.lain.engine.player.character.getDisplay
import org.lain.engine.player.character.getPhysical
import org.lain.engine.player.interaction.HandType
import org.lain.engine.player.interaction.setupHandInteractorEntity
import org.lain.engine.player.get
import org.lain.engine.player.require
import org.lain.engine.player.set
import org.lain.engine.player.toEquipmentSlotId
import org.lain.engine.script.EngineId
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerPlatform
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.packet.CharacterApplyConfirmationPacket
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.util.Color
import org.lain.engine.util.Injector
import org.lain.engine.util.math.MutableEVec3
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import java.nio.file.Path
import java.util.UUID

class CharacterInstantiationTest : EngineTest() {
    @field:TempDir
    lateinit var tempDir: Path

    private lateinit var server: EngineServer
    private lateinit var world: World
    private lateinit var platform: CharacterTestPlatform
    private var previousTransport: ServerTransportContext? = null
    private val transactions = mutableListOf<TransactionContext>()

    @BeforeEach
    fun setup() {
        previousTransport = Injector.resolveOrNull(ServerTransportContext::class)
        platform = CharacterTestPlatform()
        server = setupTestEngineServer(tempDir, platform)
        world = World(WorldId("characters"), server.simulation, server = server)
        server.simulation.loadWorld(world)
    }

    @AfterEach
    fun teardown() = runBlocking {
        if (!::server.isInitialized) return@runBlocking
        try {
            server.characterManager.close()
            awaitCharacterChanges()
            transactions.forEach { it.rollbackIfActive(IllegalStateException("test cleanup")) }
            server.update()
            server.playerPersistence.close()
            server.chunkPersistence.close()
        } finally {
            server.chat.close()
            disconnectDatabase(server.database)
            previousTransport?.let { Injector.register<ServerTransportContext>(it) }
                ?: Injector.unregister<ServerTransportContext>()
        }
    }

    @Test
    fun applyingPersistentCharacterPreservesPlayerPersistentId() {
        val player = createPlayer()
        val character = character()
        val id = OwnedCharacterId.player(player.id, character.id)
        val savedPhysical = CharacterPhysical(BodyType.BROAD, BiologicalSex.FEMALE)
        val saved = savedCharacter(id, character.data.baseLook.id, resolved = listOf(savedPhysical))

        apply(player, character, id, saved)

        assertEquals(player.id.asPersistentId(), player.require<PersistentIdComponent>().id)
        assertEquals(savedPhysical, player.require<CharacterPhysical>())
        assertEquals(player.entity, world.persistentIdToEntity[player.id.asPersistentId()])
        assertNull(world.persistentIdToEntity[id.databasePersistentId()])
    }

    @Test
    fun newCharacterAppliesProfileBaseLookAndNotifiesPlatformAfterCommit() {
        val player = createPlayer()
        val character = character()
        val id = OwnedCharacterId.player(player.id, character.id)
        val transaction = newTransaction()
        val previousInventory = player.set(TestPlayerInventory("previous-inventory"))

        server.characterManager.apply(transaction, player, character, null, id)

        assertNull(player.get<AppliedCharacter>())
        assertTrue(player.require<UsedCharacters>().characters.isEmpty())
        assertTrue(platform.appliedCharacters.isEmpty())
        assertSame(previousInventory, player.require<TestPlayerInventory>())
        assertTrue(events().isEmpty(), "Apply events must wait for the transaction commit")

        transaction.commit()

        assertEquals(AppliedCharacter(character, id), player.require<AppliedCharacter>())
        assertEquals(character.data.getPhysical(), player.require<CharacterPhysical>())
        assertEquals(character.data.getDisplay(), player.require<CharacterDisplay>())
        assertEquals(character.data.baseLook, player.require<SelectedLook>().look)
        assertTrue(player.require<Equipment>().slots.isEmpty())
        assertEquals(setOf(id), player.require<UsedCharacters>().characters)
        assertEquals(TestPlayerInventory(), player.require<TestPlayerInventory>())
        assertEquals(listOf(player.id to character), platform.appliedCharacters)
        assertEquals(listOf(CharacterApplyEvent(character, player.id)), events())
    }

    @Test
    fun savedLookIsRestoredAndUnknownLookFallsBackToBase() {
        val character = character()
        val alternative = character.data.looks.single { !it.base }
        for ((savedLook, expectedLook) in listOf(alternative.id to alternative, "removed-look" to character.data.baseLook)) {
            val player = createPlayer()
            val id = OwnedCharacterId.player(player.id, character.id)
            apply(player, character, id, savedCharacter(id, savedLook))
            assertEquals(expectedLook, player.require<SelectedLook>().look)
            assertEquals(TestPlayerInventory("saved-inventory"), player.require<TestPlayerInventory>())
        }
    }

    @Test
    fun savedEquipmentIsRestoredWithoutSharingItsMutableMap() {
        val player = createPlayer()
        val character = character()
        val id = OwnedCharacterId.player(player.id, character.id)
        val slot = EngineId("test/head").toEquipmentSlotId()
        val equipmentEntity = world.addEntity()
        val savedEquipment = mutableMapOf(slot to equipmentEntity)
        val saved = savedCharacter(id, character.data.baseLook.id).copy(equipment = savedEquipment)

        apply(player, character, id, saved)
        savedEquipment.clear()

        assertEquals(mapOf(slot to equipmentEntity), player.require<Equipment>().slots)
    }

    @Test
    fun rolledBackApplyDoesNotChangePlayerOrPublishEvent() {
        val player = createPlayerWithCharacter()
        val previous = player.require<AppliedCharacter>()
        val previousPhysical = player.require<CharacterPhysical>()
        val previousLook = player.require<SelectedLook>()
        val previousEquipment = player.require<Equipment>()
        val previousInventory = player.require<TestPlayerInventory>()
        val next = character("next")
        val id = OwnedCharacterId.player(player.id, next.id)
        val transaction = newTransaction()

        server.characterManager.apply(transaction, player, next, null, id)
        transaction.rollback(IllegalStateException("loading failed"))

        assertEquals(TransactionState.ROLLED_BACK, transaction.state)
        assertSame(previous, player.require<AppliedCharacter>())
        assertSame(previousPhysical, player.require<CharacterPhysical>())
        assertSame(previousLook, player.require<SelectedLook>())
        assertSame(previousEquipment, player.require<Equipment>())
        assertEquals(setOf(previous.id), player.require<UsedCharacters>().characters)
        assertSame(previousInventory, player.require<TestPlayerInventory>())
        assertTrue(platform.appliedCharacters.isEmpty())
        assertTrue(events().isEmpty(), "A rolled-back character must not publish an apply event")
    }

    @Test
    fun rolledBackChildReleasesCharacterReservationForAnotherPlayer() {
        val first = createPlayer()
        val second = createPlayer()
        val character = character()
        val id = OwnedCharacterId.server(character.id)
        val parent = newTransaction()
        val child = parent.child()
        server.characterManager.apply(child, first, character, null, id)
        child.commit()

        parent.rollback(IllegalStateException("parent failed"))
        apply(second, character, id)

        assertNull(first.get<AppliedCharacter>())
        assertEquals(id, second.require<AppliedCharacter>().id)
    }

    @Test
    fun occupiedCharacterRejectsAnotherPlayerWithoutChangingTheirState() {
        val first = createPlayer()
        val second = createPlayerWithCharacter()
        val previous = second.require<AppliedCharacter>()
        val shared = character("shared")
        val id = OwnedCharacterId.server(shared.id)
        apply(first, shared, id)
        val transaction = newTransaction()

        val failure = assertThrows<CharacterOccupiedException> {
            server.characterManager.apply(transaction, second, shared, null, id)
        }
        transaction.rollbackIfActive(failure)

        assertEquals(second.id, failure.player)
        assertEquals(id, failure.id)
        assertSame(previous, second.require<AppliedCharacter>())
        assertEquals(id, first.require<AppliedCharacter>().id)
    }

    @Test
    fun samePlayerCannotStartSecondApplyBeforeFirstCommit() {
        val player = createPlayer()
        val character = character()
        val id = OwnedCharacterId.player(player.id, character.id)
        val first = newTransaction()
        val second = newTransaction()
        server.characterManager.apply(first, player, character, null, id)

        val failure = assertThrows<CharacterOccupiedException> {
            server.characterManager.apply(second, player, character, null, id)
        }
        second.rollbackIfActive(failure)
        first.commit()
        apply(player, character, id)

        assertEquals(id, player.require<AppliedCharacter>().id)
        assertEquals(setOf(id), player.require<UsedCharacters>().characters)
    }

    @Test
    fun equalCharacterIdsWithDifferentPlayerOwnersDoNotConflict() {
        val first = createPlayer()
        val second = createPlayer()
        val character = character()
        apply(first, character)
        apply(second, character)
        assertFalse(first.require<AppliedCharacter>().id == second.require<AppliedCharacter>().id)
    }

    @Test
    fun switchReleasesPreviousCharacterOnlyAfterCommit() {
        val player = createPlayer()
        val competitor = createPlayer()
        val first = character("first")
        val second = character("second")
        val firstId = OwnedCharacterId.server(first.id)
        val secondId = OwnedCharacterId.server(second.id)
        apply(player, first, firstId)
        val switching = newTransaction()
        server.characterManager.apply(switching, player, second, null, secondId)
        val competing = newTransaction()
        val failure = assertThrows<CharacterOccupiedException> {
            server.characterManager.apply(competing, competitor, first, null, firstId)
        }
        competing.rollbackIfActive(failure)

        switching.commit()
        apply(competitor, first, firstId)

        assertEquals(secondId, player.require<AppliedCharacter>().id)
        assertEquals(firstId, competitor.require<AppliedCharacter>().id)
    }

    @Test
    fun failedSwitchRetainsPreviousReservationAndReleasesNewOne() {
        val player = createPlayer()
        val competitor = createPlayer()
        val first = character("first")
        val second = character("second")
        val firstId = OwnedCharacterId.server(first.id)
        val secondId = OwnedCharacterId.server(second.id)
        apply(player, first, firstId)
        val switching = newTransaction()
        server.characterManager.apply(switching, player, second, null, secondId)

        switching.rollback(IllegalStateException("switch failed"))
        apply(competitor, second, secondId)
        val competing = newTransaction()
        val failure = assertThrows<CharacterOccupiedException> {
            server.characterManager.apply(competing, competitor, first, null, firstId)
        }
        competing.rollbackIfActive(failure)

        assertEquals(firstId, player.require<AppliedCharacter>().id)
    }

    @Test
    fun releasingPlayerMakesSharedCharacterAvailable() {
        val first = createPlayer()
        val second = createPlayer()
        val character = character()
        val id = OwnedCharacterId.server(character.id)
        apply(first, character, id)

        server.characterManager.release(first)
        apply(second, character, id)

        assertEquals(id, second.require<AppliedCharacter>().id)
    }

    @Test
    fun backendFailureReportsRequestErrorWithoutTouchingInventoryOrPlayer() = runBlocking {
        val player = createPlayerWithCharacter()
        val previous = player.require<AppliedCharacter>()
        platform.validation = { _, _ -> throw IllegalStateException("backend unavailable") }

        change(player, character("next"), requestId = 41)
        awaitCharacterChanges()

        assertSame(previous, player.require<AppliedCharacter>())
        assertEquals(TestPlayerInventory("original-inventory"), player.require<TestPlayerInventory>())
        assertTrue(platform.serializedPlayers.isEmpty())
        assertTrue(platform.clearedPlayers.isEmpty())
        assertTrue(platform.openedInventories.isEmpty())
        val confirmation = confirmations().single()
        assertEquals(41L, confirmation.requestId)
        assertNotNull(confirmation.errorMessage)
    }

    @Test
    fun failedChangeCanBeRetriedWithoutRecreatingManager() = runBlocking {
        val player = createPlayerWithCharacter()
        val next = character("next")
        platform.validation = { _, _ -> throw IllegalArgumentException("invalid ticket") }
        change(player, next, requestId = 1)
        awaitCharacterChanges()
        assertNotNull(confirmations().single().errorMessage)
        platform.validation = { _, _ -> next.data }

        change(player, next, requestId = 2)
        awaitCharacterChanges()

        assertEquals(next, player.require<AppliedCharacter>().character)
        assertEquals(listOf(1L, 2L), confirmations().map { it.requestId })
        assertNull(confirmations().last().errorMessage)
        assertTrue(server.characterManager.coroutineScope.isActive)
    }

    @Test
    fun cancellingValidationDoesNotReportFailureAndAllowsRetry() = runBlocking {
        val player = createPlayerWithCharacter()
        val previous = player.require<AppliedCharacter>()
        val entered = CompletableDeferred<Unit>()
        platform.validation = { _, _ ->
            entered.complete(Unit)
            awaitCancellation()
        }
        val next = character("next")
        change(player, next, requestId = 1)
        withTimeout(5_000) { entered.await() }
        server.characterManager.coroutineScope.coroutineContext[Job]!!.children.single().cancel()
        awaitCharacterChanges()

        assertSame(previous, player.require<AppliedCharacter>())
        assertTrue(confirmations().isEmpty())
        assertTrue(platform.serializedPlayers.isEmpty())
        platform.validation = { _, _ -> next.data }
        change(player, next, requestId = 2)
        awaitCharacterChanges()
        assertEquals(next, player.require<AppliedCharacter>().character)
        assertNull(confirmations().single().errorMessage)
    }

    @Test
    fun inventorySerializationFailurePreservesOriginalCharacter() = runBlocking {
        val player = createPlayerWithCharacter()
        val previous = player.require<AppliedCharacter>()
        platform.serializationFailure = IllegalStateException("inventory cannot be serialized")

        change(player, character("next"), requestId = 9)
        awaitCharacterChanges()

        assertSame(previous, player.require<AppliedCharacter>())
        assertEquals(TestPlayerInventory("original-inventory"), player.require<TestPlayerInventory>())
        assertTrue(platform.clearedPlayers.isEmpty())
        assertTrue(platform.openedInventories.isEmpty())
        assertNotNull(confirmations().single().errorMessage)
    }

    @Test
    fun characterValidationReceivesCurrentProvidingPermission() = runBlocking {
        val player = createPlayerWithCharacter()
        val permissions = mutableListOf<Boolean>()
        platform.validation = { allowed, request ->
            permissions += allowed
            (request as ServerboundSelectedCharacter.Provided).character
        }

        platform.canProvideCharacters = false
        change(player, character("first"), requestId = 1)
        awaitCharacterChanges()
        platform.canProvideCharacters = true
        change(player, character("second"), requestId = 2)
        awaitCharacterChanges()

        assertEquals(listOf(false, true), permissions)
        assertTrue(confirmations().all { it.errorMessage == null })
    }

    @Test
    fun occupiedCharacterChangeRestoresOriginalInventoryAndReportsError() = runBlocking {
        val owner = createPlayer()
        val contender = createPlayerWithCharacter()
        val shared = character("shared")
        val sharedId = OwnedCharacterId.server(shared.id)
        apply(owner, shared, sharedId)
        val previous = contender.require<AppliedCharacter>()
        platform.clearObservations()

        server.characterManager.change(contender, provided(shared), sharedId, requestId = 17)
        awaitCharacterChanges()

        assertSame(previous, contender.require<AppliedCharacter>())
        assertEquals(sharedId, owner.require<AppliedCharacter>().id)
        assertEquals(TestPlayerInventory("original-inventory"), contender.require<TestPlayerInventory>())
        assertEquals(listOf(contender.id to "original-inventory"), platform.openedInventories)
        assertTrue(platform.clearedPlayers.isEmpty())
        assertTrue(platform.appliedCharacters.isEmpty())
        val confirmation = confirmations().single()
        assertEquals(17L, confirmation.requestId)
        assertNotNull(confirmation.errorMessage)
    }

    @Test
    fun failedPlayerPreparationRollsBackAndAllowsFreshLogin() = runBlocking {
        val playerId = PlayerId(UUID.randomUUID())
        val character = character()
        val request = provided(character)
        val settings = PlayerLoadSettings(
            playerId, emptyList(), MutableEVec3(), "CharacterTest",
            DeveloperModeStatus(), world, playerMode = PlayerMode.SPECTATOR, canProvideCharacters = true,
        )
        val failure = IllegalStateException("player initialization failed")
        platform.preparationFailure = failure

        val failed = runOnServer {
            server.playerLoader.loadPreparing(settings, PlayerLoadSettings.Account(request))
        }

        val actualFailure = assertInstanceOf(IllegalStateException::class.java, failed.exceptionOrNull())
        assertEquals(failure.message, actualFailure.message)
        assertNull(server.playerStorage.get(playerId))
        assertTrue(world.players.isEmpty())
        assertNull(world.persistentIdToEntity[playerId.asPersistentId()])
        assertFalse(world.componentManager.exists(platform.preparedPlayer!!.entity))
        assertTrue(platform.appliedCharacters.isEmpty())
        platform.preparationFailure = null

        val retried = runOnServer {
            server.playerLoader.loadPreparing(settings, PlayerLoadSettings.Account(request))
        }.getOrThrow()

        assertSame(retried, server.playerStorage.get(playerId))
        assertEquals(character.id, retried.require<AppliedCharacter>().id.character)
    }

    private fun createPlayer(): EnginePlayer {
        val playerId = PlayerId(UUID.randomUUID())
        val location = Location(MutableEVec3())
        val entity = world.addEntity {
            setComponent(PersistentIdComponent(playerId.asPersistentId()))
            setComponent(UsedCharacters(mutableSetOf()))
            setComponent(location)
            setComponent(TestPlayerInventory())
            val mainHand = world.addEntity()
            val offHand = world.addEntity()
            setComponent(
                PlayerInventory(
                    mutableSetOf(),
                    mainHand.setupHandInteractorEntity(
                        this, HandType.MAIN, offHand, persistentId("hand-main-$playerId"), location.position,
                    ),
                    offHand.setupHandInteractorEntity(
                        this, HandType.OFFHAND, mainHand, persistentId("hand-off-$playerId"), location.position,
                    ),
                )
            )
        }
        return EnginePlayer(playerId, entity, world)
    }

    private fun newTransaction(): TransactionContext =
        server.createTransactionContext(world).also { transactions += it }

    private fun createPlayerWithCharacter(): EnginePlayer = createPlayer().also {
        apply(it, character("original"))
        it.set(TestPlayerInventory("original-inventory"))
        platform.clearObservations()
        world.clearEvents()
    }

    private fun apply(
        player: EnginePlayer,
        character: ServerCharacterData,
        id: OwnedCharacterId = OwnedCharacterId.player(player.id, character.id),
        data: PlayCharacterData? = null,
    ) {
        val transaction = newTransaction()
        try {
            server.characterManager.apply(transaction, player, character, data, id)
            transaction.commit()
        } catch (failure: Throwable) {
            transaction.rollbackIfActive(failure)
            throw failure
        }
    }

    private fun savedCharacter(
        id: OwnedCharacterId,
        look: String,
        resolved: List<Component> = listOf(CharacterPhysical(BodyType.BROAD, BiologicalSex.FEMALE)),
    ) = PlayCharacterData(
        id,
        MaterializedComponents(
            id.databasePersistentId(), EntityPersistenceData.Character(look, "saved-inventory"),
            resolved, emptyList(), emptyList(),
        ),
        "saved-inventory", emptySet(), look,
    )

    private fun character(id: String = "variant"): ServerCharacterData {
        val base = Look("base", "Base", Skin("https://example.com/base.png"), "", true)
        val alternative = Look("other", "Other", Skin("https://example.com/other.png"), "", false)
        return ServerCharacterData(
            ServerCharacterId(id),
            CharacterData(
                CharacterProfile(
                    CharacterGradientName("Character", Color.WHITE), BiologicalCategory.HUMAN,
                    BiologicalSex.FEMALE, "Appearance", CharacterHeight(1.7f),
                    BodyType.NORMAL, CharacterAppearance(0.5f, 2),
                ),
                listOf(base, alternative), base,
            ),
        )
    }

    private fun provided(character: ServerCharacterData) = ServerboundSelectedCharacter.Provided(
        CharacterSource.Server(character.id), character.data,
    )

    private fun change(player: EnginePlayer, character: ServerCharacterData, requestId: Long) {
        server.characterManager.change(
            player, provided(character), OwnedCharacterId.player(player.id, character.id), requestId,
        )
    }

    private suspend fun awaitCharacterChanges() {
        withTimeout(5_000) {
            val root = server.characterManager.coroutineScope.coroutineContext[Job]!!
            while (root.children.any()) {
                server.update()
                delay(1)
            }
            server.update()
        }
    }

    private suspend fun <T> runOnServer(block: suspend () -> T): Result<T> = withTimeout(5_000) {
        coroutineScope {
            val result = async { runCatching { block() } }
            while (!result.isCompleted) {
                server.update()
                delay(1)
            }
            server.update()
            result.await()
        }
    }

    private fun events(): List<CharacterApplyEvent> =
        world.componentManager.getComponentArray<CharacterApplyEvent>().components.toList()

    private fun confirmations(): List<CharacterApplyConfirmationPacket> =
        (Injector.resolve<ServerTransportContext>() as TestServerTransportContext).sentPackets
            .mapNotNull { it.packet as? CharacterApplyConfirmationPacket }

    private data class TestPlayerInventory(val contents: String = "") : Component

    private class CharacterTestPlatform : ServerPlatform {
        val serializedPlayers = mutableListOf<PlayerId>()
        val clearedPlayers = mutableListOf<PlayerId>()
        val openedInventories = mutableListOf<Pair<PlayerId, String>>()
        val appliedCharacters = mutableListOf<Pair<PlayerId, ServerCharacterData>>()
        var serializationFailure: Throwable? = null
        var preparationFailure: Throwable? = null
        var preparedPlayer: EnginePlayer? = null
        var canProvideCharacters = true
        var validation: suspend (Boolean, ServerboundSelectedCharacter) -> CharacterData = { _, request ->
            (request as ServerboundSelectedCharacter.Provided).character
        }
        override val backend = object : EngineBackend {
            override suspend fun validateCharacter(
                canProvideCharacters: Boolean,
                request: ServerboundSelectedCharacter,
            ): CharacterData = validation(canProvideCharacters, request)
        }

        override fun beforePlayerInstantiation(player: EnginePlayer) {
            preparedPlayer = player
            player.set(TestPlayerInventory())
            preparationFailure?.let { throw it }
        }

        override fun hasPermission(player: EnginePlayer, permission: String): Boolean = canProvideCharacters

        override fun serializeInventory(player: EnginePlayer): String {
            serializedPlayers += player.id
            serializationFailure?.let { throw it }
            return player.require<TestPlayerInventory>().contents
        }

        override fun clearInventory(player: EnginePlayer) {
            clearedPlayers += player.id
            player.set(TestPlayerInventory())
        }

        override fun openInventory(player: EnginePlayer, inventory: String) {
            openedInventories += player.id to inventory
            player.set(TestPlayerInventory(inventory))
        }

        override fun onCharacterApplied(player: EnginePlayer, character: ServerCharacterData) {
            appliedCharacters += player.id to character
        }

        fun clearObservations() {
            serializedPlayers.clear()
            clearedPlayers.clear()
            openedInventories.clear()
            appliedCharacters.clear()
        }
    }
}
