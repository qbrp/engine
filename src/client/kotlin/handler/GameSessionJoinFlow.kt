package org.lain.engine.client.handler

import kotlinx.coroutines.*
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.account.ClientAuthorizedAccount
import org.lain.engine.client.account.NotAuthorizedException
import org.lain.engine.client.handler.ClientHandler.Companion.LOGGER
import org.lain.engine.client.render.ui.character.CharacterSelectionScreen
import org.lain.engine.client.script.ClientCompilation
import org.lain.engine.client.script.ClientLuaContext
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.client.util.withClientContext
import org.lain.engine.mc.commands.friendlyError
import org.lain.engine.mc.server.HttpStatusException
import org.lain.engine.player.PlayerLoadSettings
import org.lain.engine.player.account.AccountResponse
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.script.CompilationResult
import org.lain.engine.script.NamespaceHashMap
import org.lain.engine.script.NamespaceHashMapValidationResult
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.script.emptyNamespacedStorage
import org.lain.engine.script.loadContentsCompileResult
import org.lain.engine.script.lua.EngineLuaGlobals
import org.lain.engine.script.lua.FileScriptSource
import org.lain.engine.script.lua.LuaDependencies
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
        get() = state == State.CHARACTER_SELECTION

    private suspend fun handshake(authorized: ClientAuthorizedAccount?): ServerData = when (joinType) {
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

    private suspend fun listAccountCharacters(response: AccountResponse, authorized: ClientAuthorizedAccount?): List<EngineCharacter> {
        val authorizedAccount = authorized?.getAccount()
        if (joinType is JoinType.Multiplayer && authorizedAccount == null) {
            throw NotAuthorizedException()
        }
        val characters = authorizedAccount?.characters ?: response.characters
        return characters.map { it.map() }
    }

    private suspend fun acknowledge(
        authorized: ClientAuthorizedAccount?,
        namespaceHashMap: NamespaceHashMap,
        selectedCharacter: EngineCharacter?
    ): JoinGamePacket {
        return when (joinType) {
            is JoinType.Multiplayer -> {
                accountManager.sessionTicketOperation(authorized ?: throw NotAuthorizedException()) { sessionTicket ->
                    val deferred = deferJoinGamePacket()
                    handler.sendVerificationPacket(namespaceHashMap, selectedCharacter, sessionTicket)
                    deferred.await()
                }
            }
            is JoinType.Singleplayer -> {
                val integratedServer = joinType.integratedServer
                val settings = withClientContext { platform.createIntegratedServerPlayerLoadSettings(client, integratedServer) }
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
            val accountManager = client.accountManager
            val authorized = accountManager.getAuthorized()
            val account = accountManager.lastAccountResponse ?: throw NotAuthorizedException()

            val server = handshake(authorized)

            state = State.COMPILATION
            val (namespaceHashMap, compilationResult, compilation) = coroutineScope {
                val deferred = async {
                    val namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(emptyNamespacedStorage())
                    val luaContext = createLuaContext(namespacedStorage, server.id)
                    val compilation = ClientCompilation(luaContext, client)
                    val compilationResult = withClientContext {
                        val result = compilation.compileScripts()
                        namespacedStorage.loadContentsCompileResult(result)
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

            val characters = listAccountCharacters(account, authorized)
            val selectedCharacter = withClientContext {
                state = State.CHARACTER_SELECTION
                CharacterSelectionScreen.awaitCharacterSelection(
                    client,
                    null,
                    characters
                )
            }

            val (serverPlayerData, worldData, setupData, notifications) = acknowledge(authorized, namespaceHashMap, selectedCharacter)
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

    fun createLuaContext(namespacedStorage: NamespacedStorageAccess, serverId: ServerId): ClientLuaContext {
        val scriptsPath = client.resources.scripts.file
        return ClientLuaContext(
            client,
            FileScriptSource(scriptsPath.luaEntrypointDir(serverId)),
            LuaDependencies(
                EngineLuaGlobals(),
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
        AUTHORIZATION, COMPILATION, CHARACTER_SELECTION
    }

    sealed class JoinType {
        data class Singleplayer(val integratedServer: EngineServer) : JoinType()
        object Multiplayer : JoinType()
    }

    data class ServerData(val id: ServerId, val verificationNamespaceHashMap: NamespaceHashMap?)

    data class ResourceCompilationResult(
        val namespaceHashMap: NamespaceHashMap,
        val compilationResult: CompilationResult,
        val compilation: ClientCompilation
    )
}