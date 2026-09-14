package org.lain.engine.client.handler

import kotlinx.coroutines.*
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.account.CharacterSelection
import org.lain.engine.client.handler.ClientHandler.Companion.LOGGER
import org.lain.engine.client.script.ClientCompilation
import org.lain.engine.client.script.ClientLuaScriptEngine
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.client.util.withClientContext
import org.lain.engine.mc.commands.friendlyError
import org.lain.engine.server.account.HttpStatusException
import org.lain.engine.player.PlayerLoadSettings
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.script.compilation.Build
import org.lain.engine.script.FileScriptSource
import org.lain.engine.script.NamespaceHashMap
import org.lain.engine.script.NamespaceHashMapValidationResult
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.script.compilation.CompilationFailedException
import org.lain.engine.script.compilation.loadResult
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.validateNamespaceHashMap
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerId
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
    private val platform = client.infrastructure
    private val characterSelection = CharacterSelection(client)
    private val joinGamePacketConfirmation = CompletableDeferred<JoinGamePacket>()

    @Volatile
    var verificationStateStartCompletableDeferred: CompletableDeferred<GeneralServerData>? = null
        private set

    @Volatile
    var state: State = State.AUTHORIZATION
        private set

    val canCloseLevelLoadingScreen
        get() = state == State.CHARACTER_SELECTION || state == State.DONE

    private suspend fun handshake(): ServerData = when (joinType) {
        is JoinType.Multiplayer -> {
            val deferred = deferVerificationState()
            handler.sendAuthPacket(client.infrastructure.modIds)
            deferred.await()
                .let { packet ->
                    ServerData(
                        packet.serverId,
                        packet.namespaceHashMap.takeIf { packet.requireIdenticalNamespaces }
                    )
                }
        }

        is JoinType.Singleplayer -> {
            ServerData(joinType.integratedServer.globals.serverId, null)
        }
    }

    private suspend fun listAccountCharacters(): List<EngineCharacter> {
        val response = when (joinType) {
            is JoinType.Multiplayer -> {
                accountManager.requireAuthorized().getAccount()
            }

            is JoinType.Singleplayer -> {
                accountManager.getAvailableAccountResponse()
            }
        }
        return response.characters.map { it.map() }
    }

    private suspend fun acknowledge(
        namespaceHashMap: NamespaceHashMap,
        selectedCharacter: EngineCharacter?
    ): JoinGamePacket {
        return when (joinType) {
            is JoinType.Multiplayer -> {
                accountManager.sessionTicketOperation(accountManager.requireAuthorized()) { sessionTicket ->
                    handler.sendVerificationPacket(
                        namespaceHashMap,
                        selectedCharacter,
                        sessionTicket,
                    )
                    joinGamePacketConfirmation.await()
                }
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

    private suspend fun computePlayCharacter(serverId: ServerId): EngineCharacter? {
        if (joinType.isReplay) return null
        return coroutineScope {
            state = State.CHARACTER_LOAD
            val playStateDeferred = async { ServerPlayState.open(serverId) }
            val characters = async { listAccountCharacters() }
                .await()
            val previousPlayCharacter = playStateDeferred.await()?.character
                ?.let { characterId ->
                    characters.find { it.profile.id == characterId }
                }
            previousPlayCharacter ?: withClientContext {
                state = State.CHARACTER_SELECTION
                characterSelection.awaitCharacterSelection(null, characters)?.character
            }
        }
    }

    private val job = CoroutineScope(Dispatchers.IO).launch {
        try {
            accountManager.requireAccountResponse()
            val server = handshake()

            state = State.COMPILATION
            val (namespaceHashMap, compilationResult, compilation) = coroutineScope {
                val deferred = async {
                    val namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())
                    val luaContext = createLuaContext(namespacedStorage)
                    val compilation = ClientCompilation(luaContext, client)
                    val compilationResult = withClientContext {
                        val build = compilation.compileScriptsOrThrow()
                        namespacedStorage.loadResult(build)
                        build
                    }

                    // потокобезопасный доступ к namespacedStorage
                    val namespaceHashMap = namespacedStorage.get().namespaceHashMap
                    val verificationNamespaceHashMap = server.verificationNamespaceHashMap
                    if (verificationNamespaceHashMap != null) {
                        val result = validateNamespaceHashMap(namespaceHashMap, verificationNamespaceHashMap)
                        if (result is NamespaceHashMapValidationResult.Error) {
                            friendlyError(result.computeErrorMessage())
                        }
                    }

                    ResourceCompilationResult(namespaceHashMap, compilationResult, compilation)
                }

                val resourceReloadJob = launch {
                    client.resourceManager.reload(server.id)
                }

                resourceReloadJob.join()
                deferred.await()
            }

            val selectedCharacter = computePlayCharacter(server.id)

            val (serverPlayerData, worldData, setupData, notifications) = acknowledge(
                namespaceHashMap,
                selectedCharacter
            )
            withClientContext {
                val gameSession = GameSession(
                    joinType == JoinType.Multiplayer,
                    setupData.serverId,
                    setupData,
                    worldData,
                    serverPlayerData,
                    handler,
                    client,
                    compilation,
                    compilationResult,
                )

                client.joinGameSession(gameSession)

                notifications.forEach {
                    handler.applyNotification(it, false)
                }

                SERVERBOUND_JOIN_CONFIRMATION_ENDPOINT.sendC2SPacket(ConfirmationPacket)
                state = State.DONE
            }
        } catch (e: CancellationException) {
            characterSelection.cancel()
            joinGamePacketConfirmation.cancel()
            LOGGER.info("Отменен вход на сервер")
            throw e
        } catch (exception: Exception) {
            characterSelection.cancel()
            joinGamePacketConfirmation.cancel()
            withClientContext {
                client.infrastructure.disconnect(
                    if (exception is HttpStatusException) {
                        "${exception.statusCode}: ${exception.serializeApiError().message}"
                    } else if (exception is CompilationFailedException) {
                        exception.log()
                        exception.disconnectText
                    } else {
                        exception.message ?: "Неизвестная ошибка"
                    }
                )
            }
            exception.printStackTrace()
        }
    }

    fun createLuaContext(namespacedStorage: NamespacedStorageAccess): ClientLuaScriptEngine {
        return ClientLuaScriptEngine(
            client,
            FileScriptSource(FileSystem.compilationEntrypoint),
            LuaScriptEngine.Dependencies(
                LuaScriptEngine.globals(),
                namespacedStorage,
                client.luaDataStorage,
                client.moduleManager,
                FileSystem.scripts.path,
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
        AUTHORIZATION, COMPILATION, CHARACTER_LOAD, CHARACTER_SELECTION, DONE
    }

    sealed class JoinType(val isReplay: Boolean) {
        class Singleplayer(val integratedServer: EngineServer, isReplay: Boolean) : JoinType(isReplay)
        object Multiplayer : JoinType(false)
    }

    data class ServerData(val id: ServerId, val verificationNamespaceHashMap: NamespaceHashMap?)

    data class ResourceCompilationResult(
        val namespaceHashMap: NamespaceHashMap,
        val build: Build,
        val compilation: ClientCompilation
    )
}
