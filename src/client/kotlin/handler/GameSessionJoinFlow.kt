package org.lain.engine.client.handler

import kotlinx.coroutines.*
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.account.CharacterSelection
import org.lain.engine.player.character.CharacterSource
import org.lain.engine.client.handler.ClientHandler.Companion.LOGGER
import org.lain.engine.client.script.ClientCompilation
import org.lain.engine.client.script.ClientLuaScriptEngine
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.client.util.withClientContext
import org.lain.engine.mc.commands.friendlyError
import org.lain.engine.player.PlayerLoadSettings
import org.lain.engine.player.character.BackendCharacterData
import org.lain.engine.player.character.BackendCharacterId
import org.lain.engine.player.character.CharacterData
import org.lain.engine.player.character.ServerCharacterData
import org.lain.engine.player.character.ServerboundSelectedCharacter
import org.lain.engine.script.*
import org.lain.engine.script.compilation.Build
import org.lain.engine.script.compilation.CompilationFailedException
import org.lain.engine.script.compilation.loadBuild
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerId
import org.lain.engine.server.account.HttpStatusException
import org.lain.engine.server.account.SessionTicket
import org.lain.engine.transport.packet.ConfirmationPacket
import org.lain.engine.transport.packet.GeneralServerData
import org.lain.engine.transport.packet.JoinGamePacket
import org.lain.engine.transport.packet.SERVERBOUND_JOIN_CONFIRMATION_ENDPOINT
import org.lain.engine.util.file.FileSystem

class GameSessionJoinFlow(
    val joinType: JoinType,
    private val client: EngineClient,
    private val handler: ClientHandler
) {
    private val accountManager = client.accountManager
    private val platform = client.platform
    private val characterSelection = CharacterSelection(client)
    private val joinGamePacketConfirmation = CompletableDeferred<JoinGamePacket>()

    @Volatile
    var verificationStateStartCompletableDeferred: CompletableDeferred<GeneralServerData>? = null
        private set

    @Volatile
    var state: State = State.AUTHORIZATION
        private set

    val canCloseLevelLoadingScreen
        get() = state == State.CHARACTER_SELECTION || (state == State.DONE && client.gameSession?.ticks?.let { it > 2 } == true)

    private suspend fun handshake(): ServerData = when (joinType) {
        is JoinType.Multiplayer -> {
            val deferred = deferVerificationState()
            handler.sendAuthPacket(client.platform.modIds)
            deferred.await()
                .let { packet ->
                    ServerData(
                        packet.serverId,
                        packet.canProvideCharacters,
                        packet.namespaceHashMap.takeIf { packet.requireIdenticalNamespaces }
                    )
                }
        }

        is JoinType.Singleplayer -> {
            ServerData(
                joinType.integratedServer.globals.serverId,
                true,
                null
            )
        }
    }

    data class AvailableCharacters(
        val backend: Map<BackendCharacterId, BackendCharacterData>,
        val file: Map<String, CharacterData>
    )

    private suspend fun listCharacters(canProvideCharacters: Boolean): AvailableCharacters = coroutineScope {
        val backendCharacters = async {
            val response = when (joinType) {
                is JoinType.Multiplayer -> {
                    accountManager.fetchAccountResponse() ?: accountManager.requireAccountResponse()
                }

                is JoinType.Singleplayer -> {
                    accountManager.getAvailableAccountResponse()
                }
            }
            response.characters.associate { it.id to it.map() }
        }
        val clientCharacters = async {
            if (canProvideCharacters) {
                FileSystem.readCharacters()
            } else {
                emptyMap()
            }
        }
        AvailableCharacters(backendCharacters.await(), clientCharacters.await())
    }

    private suspend fun acknowledge(
        namespaceHashMap: NamespaceHashMap,
        selectedCharacter: ServerboundSelectedCharacter?
    ): JoinGamePacket {
        return when (joinType) {
            is JoinType.Multiplayer -> {
                handler.sendVerificationPacket(namespaceHashMap, selectedCharacter)
                joinGamePacketConfirmation.await()
            }

            is JoinType.Singleplayer -> {
                val integratedServer = joinType.integratedServer
                val settings = withClientContext {
                    platform.createIntegratedServerPlayerLoadSettings(
                        client,
                        integratedServer,
                    )
                }
                integratedServer.playerLoader.loadPreparing(
                    settings = settings,
                    account = PlayerLoadSettings.Account(selectedCharacter),
                )
                joinGamePacketConfirmation.await()
            }
        }
    }

    data class UsedCharacter(
        val source: CharacterSource,
        val request: ServerboundSelectedCharacter
    )

    private suspend fun computePlayCharacter(server: ServerData): UsedCharacter? {
        if (joinType.isReplay) return null
        return coroutineScope {
            state = State.CHARACTER_LOAD
            val playStateDeferred = async { ServerPlayState.open(server.id) }
            val characters = listCharacters(server.canProvideCharacters)
            val variants = CharacterSelection.variants(
                characters.backend,
                characters.file
            )

            val previousCharacter = playStateDeferred.await()
                ?.let { variants[it.character] }
                ?.let { CharacterSelection.Selection.Character(it) }

            val result = previousCharacter ?: withClientContext {
                state = State.CHARACTER_SELECTION
                characterSelection.awaitCharacterSelection(null, variants.values.toList())
            }
            result?.let {
                UsedCharacter(
                    it.variant.source,
                    it.toServerboundDto(client, joinType == JoinType.Multiplayer)
                )
            }
        }
    }

    private suspend fun compileResources(verificationNamespaceHashMap: NamespaceHashMap?): ResourceCompilationResult =
        withContext(Dispatchers.Default) {
            val namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())
            val luaContext = createLuaContext(namespacedStorage)
            val compilation = ClientCompilation(luaContext, client)

            val compilationResult = compilation.compileScriptsOrThrow()
            namespacedStorage.loadBuild(compilationResult)

            val namespaceHashMap = namespacedStorage.get().namespaceHashMap
            if (verificationNamespaceHashMap != null) {
                val result = validateNamespaceHashMap(namespaceHashMap, verificationNamespaceHashMap)
                if (result is NamespaceHashMapValidationResult.Error) {
                    friendlyError(result.computeErrorMessage())
                }
            }

            ResourceCompilationResult(namespaceHashMap, compilationResult, compilation)
        }

    private val job = CoroutineScope(Dispatchers.IO).launch {
        var sessionTicket: SessionTicket? = null
        try {
            accountManager.requireAccountResponse()
            val server = handshake()

            state = State.COMPILATION
            val (namespaceHashMap, compilationResult, compilation) = coroutineScope {
                val resourceCompilationResult = async {
                    compileResources(server.verificationNamespaceHashMap)
                }
                val resourceReloadJob = launch {
                    client.resourceManager.reload(server.id)
                }

                resourceReloadJob.join()
                resourceCompilationResult.await()
            }

            val selectedCharacter = computePlayCharacter(server)
            sessionTicket = selectedCharacter?.request?.sessionTicket?.map()

            state = State.GAME_LOADING
            val (serverPlayerData, worldData, setupData, notifications) = acknowledge(
                namespaceHashMap,
                selectedCharacter?.request
            )
            withClientContext {
                val gameSession = GameSession(
                    joinType == JoinType.Multiplayer,
                    setupData.serverId,
                    setupData,
                    worldData,
                    serverPlayerData,
                    serverPlayerData.character?.let {
                        selectedCharacter ?: return@let null
                        LocalCharacter(selectedCharacter.source)
                    },
                    handler,
                    client,
                    compilation,
                    compilationResult,
                    joinType,
                )

                client.joinGameSession(gameSession)

                notifications.forEach {
                    handler.applyNotification(it, false)
                }

                SERVERBOUND_JOIN_CONFIRMATION_ENDPOINT.sendC2SPacket(ConfirmationPacket)
                state = State.DONE
            }
        } catch (e: CancellationException) {
            LOGGER.info("Отменен вход на сервер")
            characterSelection.cancel()
            joinGamePacketConfirmation.cancel()
            sessionTicket?.let {
                accountManager.getAuthorized()?.revokeSessionTicket(it.hash)
            }
            throw e
        } catch (exception: Exception) {
            characterSelection.cancel()
            joinGamePacketConfirmation.cancel()
            withClientContext {
                client.platform.disconnect(
                    if (exception is HttpStatusException) {
                        "${exception.statusCode}: ${exception.serializeApiError().message}"
                    } else {
                        if (exception is CompilationFailedException) exception.log()
                        exception.message ?: "Неизвестная ошибка"
                    }
                )
            }
            exception.printStackTrace()
        }
    }

    fun createLuaContext(namespacedStorage: NamespacedStorageAccess): ClientLuaScriptEngine {
        client.moduleManager.composeModules()
        return ClientLuaScriptEngine(
            client,
            FileScriptSource(FileSystem.compilationEntrypoint),
            LuaScriptEngine.Dependencies(
                LuaScriptEngine.globals(),
                namespacedStorage,
                client.luaDataStorage,
                client.moduleManager,
                scriptsPath = FileSystem.scripts.path,
                compilationManifestFile =
                    FileSystem.clientCompilationManifestFile(FileSystem.scripts),
            )
        ).also { it.setup() }
    }

    fun deferVerificationState(): Deferred<GeneralServerData> {
        val deferred = CompletableDeferred<GeneralServerData>()
        verificationStateStartCompletableDeferred = deferred
        return deferred
    }

    fun confirmJoinGamePacket(packet: JoinGamePacket): Boolean {
        characterSelection.confirm()
        return joinGamePacketConfirmation.complete(packet)
    }

    fun cancel() {
        characterSelection.cancel()
        joinGamePacketConfirmation.cancel()
        job.cancel()
    }

    enum class State {
        AUTHORIZATION, COMPILATION, CHARACTER_LOAD, CHARACTER_SELECTION, GAME_LOADING, DONE
    }

    sealed class JoinType(val isReplay: Boolean) {
        class Singleplayer(val integratedServer: EngineServer, isReplay: Boolean) : JoinType(isReplay)
        object Multiplayer : JoinType(false)
    }

    data class ServerData(
        val id: ServerId,
        val canProvideCharacters: Boolean,
        val verificationNamespaceHashMap: NamespaceHashMap?
    )

    data class ResourceCompilationResult(
        val namespaceHashMap: NamespaceHashMap,
        val build: Build,
        val compilation: ClientCompilation
    )
}
