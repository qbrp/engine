package org.lain.engine.client.handler

import kotlinx.coroutines.*
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.handler.ClientHandler.Companion.LOGGER
import org.lain.engine.client.render.ui.character.CharacterSelectionScreen
import org.lain.engine.client.script.ClientCompilation
import org.lain.engine.client.script.ClientLuaScriptEngine
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.client.util.withClientContext
import org.lain.engine.mc.commands.friendlyError
import org.lain.engine.mc.server.HttpStatusException
import org.lain.engine.mc.server.SetupException
import org.lain.engine.player.PlayerLoadSettings
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.script.CompilationException
import org.lain.engine.script.CompilationResult
import org.lain.engine.script.FileScriptSource
import org.lain.engine.script.NamespaceHashMap
import org.lain.engine.script.NamespaceHashMapValidationResult
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.script.loadCompilationResult
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.luaEntrypointDir
import org.lain.engine.script.validateNamespaceHashMap
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerId
import org.lain.engine.transport.packet.ConfirmationPacket
import org.lain.engine.transport.packet.GeneralServerData
import org.lain.engine.transport.packet.JoinGamePacket
import org.lain.engine.transport.packet.SERVERBOUND_JOIN_CONFIRMATION_ENDPOINT

class GameSessionJoinFlow(
    val joinType: JoinType,
    private val client: EngineClient,
    private val handler: ClientHandler
) {
    private val accountManager = client.accountManager
    private val platform = client.infrastructure

    @Volatile
    var verificationStateStartCompletableDeferred: CompletableDeferred<GeneralServerData>? = null
        private set

    @Volatile
    var joinGamePacketCompletableDeferred: CompletableDeferred<JoinGamePacket>? = null
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
                    val deferred = deferJoinGamePacket()
                    handler.sendVerificationPacket(namespaceHashMap, selectedCharacter, sessionTicket)
                    deferred.await()
                }
            }

            is JoinType.Singleplayer -> {
                val integratedServer = joinType.integratedServer
                val settings =
                    withClientContext { platform.createIntegratedServerPlayerLoadSettings(client, integratedServer) }
                val joinGamePacketDefer = deferJoinGamePacket()
                integratedServer.playerLoader.loadPreparing(
                    settings = settings,
                    account = PlayerLoadSettings.Account(selectedCharacter),
                )
                joinGamePacketDefer.await()
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
                    val luaContext = createLuaContext(namespacedStorage, server.id)
                    val compilation = ClientCompilation(luaContext, client)
                    val compilationResult = withClientContext {
                        val result = compilation.compileScripts()
                        if (result.exceptions.isNotEmpty()) {
                            result.logExceptions()
                            throw SetupException(result.exceptions)
                        }
                        namespacedStorage.loadCompilationResult(result)
                        result
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

            val selectedCharacter = if (!joinType.isReplay) {
                state = State.CHARACTER_LOAD
                val characters = listAccountCharacters()
                withClientContext {
                    state = State.CHARACTER_SELECTION
                    CharacterSelectionScreen.awaitCharacterSelection(
                        client,
                        null,
                        characters
                    )
                }
            } else {
                null
            }

            val (serverPlayerData, worldData, setupData, notifications) = acknowledge(
                namespaceHashMap,
                selectedCharacter
            )
            withClientContext {
                val gameSession = GameSession(
                    setupData.serverId,
                    setupData,
                    worldData,
                    serverPlayerData,
                    handler,
                    client,
                    compilation,
                    compilationResult
                )

                client.joinGameSession(gameSession)

                notifications.forEach {
                    handler.applyNotification(it, false)
                }

                SERVERBOUND_JOIN_CONFIRMATION_ENDPOINT.sendC2SPacket(ConfirmationPacket)
                state = State.DONE
            }
        } catch (e: CancellationException) {
            LOGGER.info("Отменена корутина входа на сервер")
            throw e
        } catch (exception: Exception) {
            withClientContext {
                client.infrastructure.disconnect(
                    if (exception is HttpStatusException) {
                        "${exception.statusCode}: ${exception.serializeApiError().message}"
                    } else {
                        exception.message ?: "Неизвестная ошибка"
                    }
                )
            }
            exception.printStackTrace()
        }
    }

    fun createLuaContext(namespacedStorage: NamespacedStorageAccess, serverId: ServerId): ClientLuaScriptEngine {
        val scriptsPath = client.resources.scripts.file
        return ClientLuaScriptEngine(
            client,
            FileScriptSource(scriptsPath.luaEntrypointDir(serverId)),
            LuaScriptEngine.Dependencies(
                LuaScriptEngine.globals(),
                namespacedStorage,
                scriptsPath.path,
                client.luaDataStorage
            )
        ).also { it.setup() }
    }

    fun deferVerificationState(): Deferred<GeneralServerData> {
        val deferred = CompletableDeferred<GeneralServerData>()
        verificationStateStartCompletableDeferred = deferred
        return deferred
    }

    fun deferJoinGamePacket(): Deferred<JoinGamePacket> {
        val deferred = CompletableDeferred<JoinGamePacket>()
        joinGamePacketCompletableDeferred = deferred
        return deferred
    }

    fun cancel() {
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
        val compilationResult: CompilationResult,
        val compilation: ClientCompilation
    )
}