package org.lain.engine.client.handler

import kotlinx.coroutines.*
import org.lain.engine.Constants
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.account.CharacterSelection
import org.lain.engine.player.character.CharacterSource
import org.lain.engine.client.handler.ClientHandler.Companion.LOGGER
import org.lain.engine.client.script.ClientLuaScriptEngine
import org.lain.engine.client.script.compileScriptsOrThrow
import org.lain.engine.client.transport.ClientTransport
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.client.util.withClientContext
import org.lain.engine.mc.commands.friendlyError
import org.lain.engine.mc.server.ClientHelloPacket
import org.lain.engine.mc.server.SERVERBOUND_CLIENT_HELLO_ENDPOINT
import org.lain.engine.player.PlayerLoadSettings
import org.lain.engine.player.account.AccountResponse
import org.lain.engine.player.character.BackendCharacterData
import org.lain.engine.player.character.BackendCharacterId
import org.lain.engine.player.character.CharacterData
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
import org.lain.engine.transport.packet.CLIENTBOUND_JOIN_GAME_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_SERVER_HELLO_ENDPOINT
import org.lain.engine.transport.packet.ConfirmationPacket
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.transport.packet.JoinGamePacket
import org.lain.engine.transport.packet.LoginRequestPacket
import org.lain.engine.transport.packet.SERVERBOUND_JOIN_CONFIRMATION_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_LOGIN_REQUEST_PACKET
import org.lain.engine.util.Injector
import org.lain.engine.util.file.FileSystem
import kotlin.time.Duration.Companion.seconds

class GameSessionJoinFlow(
    val joinType: JoinType,
    val client: EngineClient,
    private val transport: ClientTransport
) {
    init {
        Injector.register<ClientTransport>(transport)
    }

    private val accountManager = client.accountManager
    private val platform = client.platform
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val requests = ClientRequestHandler(transport)
    private val sessionPackets = SessionPacketReceiver(transport)

    private var characterSelection: CharacterSelection? = null
    private var transportClosed = false

    @Volatile
    var state: State = State.COMPILATION
        private set
    val progress = JoinFlowProgress()

    val canCloseLevelLoadingScreen
        get() = state == State.CHARACTER_SELECTION || (state == State.DONE && client.gameSession?.ticks?.let { it > 2 } == true)

    private fun <T> CoroutineScope.observableAsync(block: suspend CoroutineScope.() -> T) = async {
        block()
    }.also {
        it.invokeOnCompletion { progress.commit() }
    }

    private suspend fun setupNetwork() = withClientContext {
        requests.register(CLIENTBOUND_SERVER_HELLO_ENDPOINT)
        requests.register(CLIENTBOUND_JOIN_GAME_ENDPOINT)
        sessionPackets.runEndpoints()
    }

    private suspend fun authorize(): AccountResponse {
        return when (joinType) {
            is JoinType.Multiplayer -> accountManager.fetchAccountResponse() ?: accountManager.requireAccountResponse()
            is JoinType.Singleplayer -> accountManager.getAvailableAccountResponse()
        }
    }

    private suspend fun handshake(): ServerData = when (joinType) {
        is JoinType.Multiplayer -> {
            requests.await(CLIENTBOUND_SERVER_HELLO_ENDPOINT) {
                transport.sendServerboundPacket(
                    SERVERBOUND_CLIENT_HELLO_ENDPOINT,
                    ClientHelloPacket(client.platform.modIds, Constants.ENGINE_MOD_VERSION)
                )
            }
                .let {
                    val server = it.server
                    ServerData(
                        server.serverId,
                        server.canProvideCharacters,
                        server.namespaceHashMap.takeIf { server.requireIdenticalNamespaces }
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
    }.also { progress.commit() }

    data class AvailableCharacters(
        val backend: Deferred<Map<BackendCharacterId, BackendCharacterData>>,
        val file: Deferred<Map<String, CharacterData>>
    )

    private fun CoroutineScope.listCharacters(
        account: Deferred<AccountResponse>,
        canProvideCharacters: Boolean
    ): AvailableCharacters {
        val backendCharacters = async { account.await().characters.associate { it.id to it.map() } }
        val clientCharacters = observableAsync {
            if (canProvideCharacters) {
                FileSystem.readCharacters()
            } else {
                emptyMap()
            }
        }
        return AvailableCharacters(backendCharacters, clientCharacters)
    }

    private suspend fun acknowledge(
        namespaceHashMap: NamespaceHashMap,
        selectedCharacter: ServerboundSelectedCharacter?
    ): JoinGamePacket {
        return when (joinType) {
            is JoinType.Multiplayer -> {
                requests.await(CLIENTBOUND_JOIN_GAME_ENDPOINT) {
                    transport.sendServerboundPacket(
                        SERVERBOUND_LOGIN_REQUEST_PACKET,
                        LoginRequestPacket(
                            developerModeStatus = DeveloperModeStatus(client.developerMode, client.acousticDebug),
                            namespaces = namespaceHashMap,
                            character = selectedCharacter,
                        )
                    )
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
                requests.await(CLIENTBOUND_JOIN_GAME_ENDPOINT) {
                    integratedServer.playerLoader.loadPreparing(
                        settings = settings,
                        account = PlayerLoadSettings.Account(selectedCharacter),
                    )
                }
            }
        }.also { progress.commit() }
    }

    data class UsedCharacter(
        val source: CharacterSource,
        val request: ServerboundSelectedCharacter
    )

    private suspend fun CoroutineScope.computePlayCharacter(
        account: Deferred<AccountResponse>,
        resourceLoad: ResourceLoad,
        server: ServerData
    ): UsedCharacter? {
        if (joinType.isReplay) return null

        val characters = listCharacters(account, server.canProvideCharacters)
        val variants = CharacterSelection.variants(
            characters.backend.await(),
            characters.file.await()
        )

        val serverPlayState = resourceLoad.playState.await()
        val previousCharacter = serverPlayState
            ?.let { variants[it.character] }
            ?.let { CharacterSelection.Selection.Character(it) }

        val result = previousCharacter ?: withClientContext {
            val namespacedStorage = resourceLoad.scripts.await().luaContext.namespacedStorage

            state = State.CHARACTER_SELECTION
            val characterSelection = CharacterSelection(client, namespacedStorage, serverPlayState)
                .also { characterSelection = it }

            characterSelection.awaitCharacterSelection(null, variants.values.toList())
        }

        return result?.let {
            UsedCharacter(
                it.variant.source,
                it.toServerboundDto(client, joinType == JoinType.Multiplayer)
            )
        }
    }

    private suspend fun compileScripts(verificationNamespaceHashMap: NamespaceHashMap?): ScriptCompilationResult =
        withContext(Dispatchers.Default) {
            val namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())
            val luaContext = createLuaContext(namespacedStorage)

            val compilationResult = luaContext.compileScriptsOrThrow()
            namespacedStorage.loadBuild(compilationResult)

            val namespaceHashMap = namespacedStorage.get().namespaceHashMap
            if (verificationNamespaceHashMap != null) {
                val result = validateNamespaceHashMap(namespaceHashMap, verificationNamespaceHashMap)
                if (result is NamespaceHashMapValidationResult.Error) {
                    friendlyError(result.computeErrorMessage())
                }
            }

            ScriptCompilationResult(namespaceHashMap, compilationResult, luaContext)
        }

    private val job = coroutineScope.launch {
        var sessionTicket: SessionTicket? = null
        try {
            setupNetwork()
            val account = observableAsync { authorize() }
            val server = handshake()

            state = State.COMPILATION

            val resourceLoad = ResourceLoad(
                scripts = observableAsync { compileScripts(server.verificationNamespaceHashMap) },
                playState = observableAsync { ServerPlayState.open(server.id) },
                parallelTask = observableAsync { client.resourceManager.reload(server.id) },
            )
            val selectedCharacter = computePlayCharacter(account, resourceLoad, server)
            sessionTicket = selectedCharacter?.request?.sessionTicket?.map()

            val scripts = resourceLoad.scripts.await()
            state = State.GAME_LOADING
            val (serverPlayerData, worldData, setupData, notifications) = acknowledge(
                scripts.namespaceHashMap,
                selectedCharacter?.request
            )

            characterSelection?.confirm()
            resourceLoad.parallelTask.join()
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
                    transport,
                    sessionPackets,
                    client,
                    scripts.luaContext,
                    scripts.build,
                    joinType,
                    resourceLoad.playState.await()
                        ?: ServerPlayState(selectedCharacter?.source, emptyMap()),
                )

                sessionPackets.bind(gameSession)
                client.joinGameSession(gameSession)

                notifications.forEach {
                    gameSession.handler.applyNotification(it, false)
                }

                SERVERBOUND_JOIN_CONFIRMATION_ENDPOINT.sendC2SPacket(ConfirmationPacket)
                state = State.DONE
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) {
                LOGGER.info("Отменен вход на сервер")
                throw exception
            }

            LOGGER.error("Ошибка входа на сервер на этапе $state", exception)
            if (exception is CompilationFailedException) exception.log()
            val reason = if (exception is HttpStatusException) {
                val message = runCatching { exception.serializeApiError().message }
                    .getOrElse { exception.message ?: "Ошибка HTTP" }
                "${exception.statusCode}: $message"
            } else {
                exception.message ?: "Неизвестная ошибка"
            }

            withClientContext {
                closeTransport()
                client.platform.disconnect(reason)
            }
        } finally {
            requests.close()
            if (state != State.DONE) {
                sessionPackets.close()
                characterSelection?.cancel()
                sessionTicket?.let { ticket ->
                    withContext(NonCancellable) {
                        try {
                            withTimeout(5.seconds) {
                                accountManager.getAuthorized()?.revokeSessionTicket(ticket.hash)
                            }
                        } catch (exception: Exception) {
                            LOGGER.warn("Не удалось отозвать билет сессии после прерванного входа", exception)
                        }
                    }
                }
            }
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

    fun cancel() {
        job.cancel()
        coroutineScope.cancel()
        requests.close()
        characterSelection?.cancel()
        closeTransport()
    }

    private fun closeTransport() {
        if (transportClosed) return
        transportClosed = true
        sessionPackets.close()
        transport.unregisterAll()
        Injector.unregister<ClientTransport>()
    }

    enum class State {
        COMPILATION,
        CHARACTER_SELECTION,
        GAME_LOADING,
        DONE;
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

    data class ResourceLoad(
        val scripts: Deferred<ScriptCompilationResult>,
        val playState: Deferred<ServerPlayState?>,
        val parallelTask: Job
    )

    data class ScriptCompilationResult(
        val namespaceHashMap: NamespaceHashMap,
        val build: Build,
        val luaContext: ClientLuaScriptEngine,
    )
}
