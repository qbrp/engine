package org.lain.engine.mc.server

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.minecraft.server.level.ServerPlayer
import org.lain.engine.Constants
import org.lain.engine.mc.commands.friendlyError
import org.lain.engine.mc.engineId
import org.lain.engine.mc.getPlayer
import org.lain.engine.mc.hasPermission
import org.lain.engine.mc.isOp
import org.lain.engine.mc.players
import org.lain.engine.mc.sendMessage
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerLoadSettings
import org.lain.engine.player.Username
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.script.NamespaceHashMapValidationResult
import org.lain.engine.script.validateNamespaceHashMap
import org.lain.engine.server.Notification
import org.lain.engine.server.account.DedicatedEngineAccountService
import org.lain.engine.server.account.SessionTicket
import org.lain.engine.server.network
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.network.ConnectionSession
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.transport.network.ServerNetworkTransport
import org.lain.engine.transport.network.SessionId
import org.lain.engine.transport.packet.CLIENTBOUND_VERIFICATION_ENDPOINT
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.transport.packet.GeneralServerData
import org.lain.engine.transport.packet.SERVERBOUND_VERIFICATION_RESPONSE_ENDPOINT
import org.lain.engine.transport.packet.VerificationDataPacket
import org.lain.engine.transport.packet.VerificationResponsePacket
import java.util.UUID


class DedicatedEngineMinecraftServer(
    dependencies: EngineMinecraftServerDependencies,
    override val connectionManager: ServerConnectionManager = ServerConnectionManager(
        dependencies.playerStorage,
        dependencies.minecraftServer,
        dependencies.entityTable
    ),
    override val transportContext: ServerTransportContext = ServerNetworkTransport(
        dependencies.minecraftServer,
        connectionManager,
        dependencies.playerStorage
    ),
) : EngineMinecraftServer(dependencies) {
    private val accountService = DedicatedEngineAccountService()
    val authorizationListener = ServerAuthorizationListener(
        connectionManager,
        this,
        accountService
    )

    override fun tick() {
        for (player in engine.playerStorage.getAll()) {
            val network = player.network
            if (!network.authorized) {
                network.tickTimeout -= 1
                if (network.tickTimeout <= 0) {
                    val entity = entityTable.getEntity(player.id) ?: continue
                    connectionManager.disconnect(
                        connectionManager.getSession(player.id),
                        "Время ожидания подтверждения входа в игру истекло"
                    )
                    onLeavePlayer(entity)
                }
            }
        }
        super.tick()
    }

    override fun run() {
        super.run()
        authorizationListener.run()
    }

    override fun disable() {
        authorizationListener.stop()
        super.disable()
    }

    override fun onJoinPlayer(entity: ServerPlayer) {
        connectionManager.addConnectionSession(
            ConnectionSession(
                SessionId(UUID.randomUUID()),
                Username(entity.name.string),
                entity.engineId,
                entity.isOp
            )
        )
        super.onJoinPlayer(entity)
    }

    override fun onLeavePlayer(entity: ServerPlayer) {
        // Уничтожаем в первую очередь запись PlayerId -> Entity
        // Она создаётся до инстанцирования игрока (см. ServerAuthorizationListener)
        val id = entity.engineId
        connectionManager.getSessionOrNull(id)?.let { authorizationListener.onDisconnect(it.uuid) }
        if (playerStorage.get(id) == null) {
            entityTable.removePlayer(id)
        }
        super.onLeavePlayer(entity)
        connectionManager.removeConnectionSession(entity.engineId)
    }

    override suspend fun validateCharacter(
        player: EnginePlayer,
        characterId: String,
        character: EngineCharacter?,
        sessionTicket: SessionTicket?
    ): EngineCharacter {
        return accountService
            .getAuthorized(sessionTicket ?: error("Не указан сессионный тикет"))
            .getCharacter(characterId)
            .map()
    }
}

@Serializable
data class AuthPacket(
    val mods: List<String>,
    val version: String
) : Packet {
    override val requireAuthorized: Boolean = false
}

val SERVERBOUND_AUTH_ENDPOINT = Endpoint<AuthPacket>()

class ServerAuthorizationListener(
    private val connectionManager: ServerConnectionManager,
    private val server: DedicatedEngineMinecraftServer,
    private val accountService: DedicatedEngineAccountService
) {
    private val supervisorJob = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private val authorizationJobs = AwaitingResponseJobs<SessionId, VerificationResponsePacket>(coroutineScope)

    fun run() {
        SERVERBOUND_AUTH_ENDPOINT.registerReceiver { ctx ->
            val packet = this
            val playerId = ctx.sender
            val entity = server.minecraftServer.getPlayer(playerId)
                ?: error("Игрок ${ctx.sender} не находится на сервере или не найден")
            onAuth(packet, entity, playerId)
        }
        SERVERBOUND_VERIFICATION_RESPONSE_ENDPOINT.registerReceiver { ctx ->
            onVerificationResponse(this, ctx.sender)
        }
    }

    fun onDisconnect(sessionId: SessionId) {
        authorizationJobs.cancel(sessionId)
    }

    fun stop() {
        authorizationJobs.cancelAll()
        supervisorJob.cancel()
    }

    private fun onAuth(packet: AuthPacket, entity: ServerPlayer, id: PlayerId) {
        val connection = connectionManager.getSession(id)
        if (authorizationJobs.isPending(connection.uuid)) {
            friendlyError("Авторизация игрока уже выполняется")
        }
        if (server.engine.playerStorage.get(id) != null) {
            friendlyError("Игрок уже авторизован")
        }

        if (packet.version !in Constants.ALLOWED_VERSIONS) {
            val versionsText = if (Constants.ALLOWED_VERSIONS.size == 1) {
                Constants.ALLOWED_VERSIONS.first()
            } else {
                "одна из следующих: ${Constants.ALLOWED_VERSIONS.map { "<newline>$it" }}"
            }
            friendlyError("Несовместимая версия. Установлена ${packet.version}, в то время как требуется $versionsText")
        }

        // Запись в entityTable снимается в DedicatedEngineMinecraftServer.onLeavePlayer
        val engine = server.engine
        server.entityTable.setEntity(entity, id)

        val mods = packet.mods
        val minimapPermission = entity.isOp || entity.hasPermission("minimap")
        val hasMinimap =
            mods.contains("xaeroworldmap") || mods.contains("xaerominimap") || mods.contains("voxelmap") || mods.contains(
                "journeymap"
            )
        if (!minimapPermission && hasMinimap) {
            friendlyError(
                "<bold>Вы были исключены с сервера из-за мода на мини-карту</bold><newline>$MINIMAP_WARNING"
            )
        }
        connection.mods = mods.toSet()

        val verificationData = GeneralServerData(
            engine.globals.serverId,
            engine.globals.requireIdenticalNamespaces,
            engine.namespacedStorage.get().namespaceHashMap,
        )
        val job = authorizationJobs.start(connection.uuid) { verificationResponse ->
            runAuthorization(connection, verificationData, verificationResponse)
        }
        if (job == null) friendlyError("Авторизация игрока уже выполняется")
    }

    private fun onVerificationResponse(packet: VerificationResponsePacket, playerId: PlayerId) {
        val connection = connectionManager.getSession(playerId)
        if (!authorizationJobs.complete(connection.uuid, packet)) {
            friendlyError("Ответ верификации получен до начала авторизации или повторно")
        }
    }

    private suspend fun runAuthorization(
        connection: ConnectionSession,
        verificationData: GeneralServerData,
        verificationResponse: Deferred<VerificationResponsePacket>,
    ) {
        try {
            CLIENTBOUND_VERIFICATION_ENDPOINT.sendS2C(
                VerificationDataPacket(verificationData),
                connection.playerId,
            )
            finishAuthorization(connection, verificationData, verificationResponse.await())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            withContext(server.engine.dispatcher) {
                val currentSession = connectionManager.getSessionOrNull(connection.playerId)
                if (currentSession?.uuid == connection.uuid) {
                    connectionManager.disconnect(connection.playerId, e)
                }
            }
        }
    }

    private suspend fun finishAuthorization(
        connection: ConnectionSession,
        verificationData: GeneralServerData,
        response: VerificationResponsePacket,
    ) {
        val engine = server.engine
        val settings = withContext(engine.dispatcher) {
            requireCurrentSession(connection)
            val entity = server.minecraftServer.getPlayer(connection.playerId)
                ?: throw CancellationException("Игрок покинул сервер во время авторизации")

            if (verificationData.requireIdenticalNamespaces) {
                val validationResult = validateNamespaceHashMap(
                    response.namespaces,
                    verificationData.namespaceHashMap,
                )
                if (validationResult is NamespaceHashMapValidationResult.Error) {
                    friendlyError(validationResult.computeErrorMessage())
                }
            }

            val notifications = mutableListOf<Notification>()
            if (connection.mods.contains("freecam")) {
                notifyOperators("Freecam", connection.username)
                notifications += Notification.FREECAM
            }

            engine.serverMinecraftPlayerLoadSettings(
                entity,
                connection.playerId,
                response.developerModeStatus,
                notifications,
            )
        }

        val account = accountService
            .getAuthorized(response.sessionTicket.map())
            .getAccount()
        val character = response.characterId?.let { characterId ->
            val characterData = account.characters.firstOrNull { it.profile.id == characterId }
                ?: friendlyError("Персонаж $characterId не принадлежит авторизованному аккаунту")
            characterData.map()
        }

        withContext(engine.dispatcher) {
            requireCurrentSession(connection)
            if (engine.playerStorage.get(connection.playerId) != null) {
                friendlyError("Игрок уже авторизован")
            }
        }
        engine.playerLoader.loadPreparing(
            settings = settings,
            account = PlayerLoadSettings.Account(character),
        )
    }

    private fun requireCurrentSession(connection: ConnectionSession) {
        val currentSession = connectionManager.getSessionOrNull(connection.playerId)
        if (currentSession?.uuid != connection.uuid) {
            throw CancellationException("Сессия игрока изменилась во время авторизации")
        }
    }

    internal fun notifyOperators(id: String, username: Username) {
        server.minecraftServer.players
            .filter { it.isOp }
            .forEach { it.sendMessage("<red>[!]<reset><gold> $username зашел на сервер с $id</gold>") }
    }

    companion object {
        val MINIMAP_WARNING =
            """Моды на мини-карты запрещены, поскольку позволяют получать информацию о местонахождении игроков и построек. 
      |Это нарушает принцип мета-информации и невозможно по РП: ваш персонаж в теории не способен знать это.
    """.trimMargin()
    }
}
