package org.lain.engine.mc.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
import org.lain.engine.mc.server.DedicatedEngineAccountService
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerLoadSettings
import org.lain.engine.player.Username
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.script.NamespaceHashMap
import org.lain.engine.script.NamespaceHashMapValidationResult
import org.lain.engine.script.validateNamespaceHashMap
import org.lain.engine.server.Notification
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
        if (playerStorage.get(id) == null) {
            entityTable.removePlayer(id)
        }
        super.onLeavePlayer(entity)
        connectionManager.removeConnectionSession(entity.engineId)
    }

    override suspend fun validateCharacter(
        player: EnginePlayer,
        characterId: String,
        character: EngineCharacter?
    ): EngineCharacter {
        val session = connectionManager.getSession(player.id)
        return accountService
            .getAuthorized(session.sessionTicket!!)
            .getCharacter(characterId)
            .map()
    }
}

@Serializable
data class AuthPacket(
    val mods: List<String>,
    val version: String,
    val sessionTicket: String
) : Packet

val SERVERBOUND_AUTH_ENDPOINT = Endpoint<AuthPacket>()

class ServerAuthorizationListener(
    private val connectionManager: ServerConnectionManager,
    private val server: DedicatedEngineMinecraftServer,
    private val accountService: DedicatedEngineAccountService
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private suspend fun runCatching(connectionSession: ConnectionSession, statement: suspend () -> Unit) {
        try {
            statement()
        } catch (e: Throwable) {
            server.engine.execute {
                connectionManager.disconnect(
                    connectionSession,
                    "Не удалось авторизоваться из-за внутренней ошибки сервера"
                )
                e.printStackTrace()
            }
        }
    }

    fun run() {
        SERVERBOUND_AUTH_ENDPOINT.registerReceiver { ctx ->
            val packet = this
            val playerId = ctx.sender
            val entity = server.minecraftServer.getPlayer(playerId)
                ?: error("Игрок ${ctx.sender} не находится на сервере или не найден")
            onAuth(packet, entity, playerId)
        }
        SERVERBOUND_VERIFICATION_RESPONSE_ENDPOINT.registerReceiver { ctx ->
            val playerId = ctx.sender
            val entity = server.minecraftServer.getPlayer(playerId)
                ?: error("Игрок ${ctx.sender} не находится на сервере или не найден")
            onVerificationResponse(developerModeStatus, namespaces, entity, playerId, characterId)
        }
    }

    private fun onAuth(packet: AuthPacket, entity: ServerPlayer, id: PlayerId) {
        val connection = connectionManager.getSession(id)
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
            connectionManager.disconnect(
                connection,
                "<bold>Вы были исключены с сервера из-за мода на мини-карту</bold><newline>$MINIMAP_WARNING"
            )
        }
        connection.mods = mods.toSet()

        coroutineScope.launch {
            runCatching(connection) {
                val authorized = accountService.getAuthorized(packet.sessionTicket)
                authorized.getAccount() // проверка на валидность

                CLIENTBOUND_VERIFICATION_ENDPOINT.sendS2C(
                    VerificationDataPacket(
                        GeneralServerData(
                            engine.globals.serverId,
                            engine.globals.requireIdenticalNamespaces,
                            engine.namespacedStorage.get().namespaceHashMap
                        )
                    ),
                    id
                )
            }
        }
    }

    private fun onVerificationResponse(
        developerModeStatus: DeveloperModeStatus,
        playerNamespaceHashMap: NamespaceHashMap,
        entity: ServerPlayer,
        playerId: PlayerId,
        selectedCharacter: String
    ) {
        val engine = server.engine
        val connection = connectionManager.getSession(playerId)
        val ticket = connection.sessionTicket!!
        if (engine.globals.requireIdenticalNamespaces) {
            val serverNamespacesHashMap = engine.namespacedStorage.get().namespaceHashMap
            val validationResult = validateNamespaceHashMap(playerNamespaceHashMap, serverNamespacesHashMap)
            if (validationResult is NamespaceHashMapValidationResult.Error) {
                friendlyError(validationResult.computeErrorMessage())
            }
        }
        val username = connection.username
        val notifications = mutableListOf<Notification>()
        if (connection.mods.contains("freecam")) {
            notifyOperators("Freecam", username)
            notifications += Notification.FREECAM
        }

        val settings = engine.serverMinecraftPlayerLoadSettings(entity, playerId, developerModeStatus, notifications)
        coroutineScope.launch {
            val character = accountService.getAuthorized(ticket)
                .getCharacter(selectedCharacter)
                .map()
            engine.playerLoader.loadPreparing(
                settings = settings,
                account = PlayerLoadSettings.Account(character),
                exceptionHandler = { connectionManager.disconnect(playerId, it) }
            )
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