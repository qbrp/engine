package org.lain.engine.mc.server

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.minecraft.server.level.ServerPlayer
import org.lain.engine.Constants
import org.lain.engine.mc.commands.FriendlyException
import org.lain.engine.mc.commands.friendlyError
import org.lain.engine.mc.getPlayer
import org.lain.engine.mc.hasPermission
import org.lain.engine.mc.isOp
import org.lain.engine.mc.players
import org.lain.engine.mc.sendMessage
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerLoadSettings
import org.lain.engine.player.Username
import org.lain.engine.player.character.CHARACTER_PROVIDING_PERMISSION
import org.lain.engine.script.NamespaceHashMapValidationResult
import org.lain.engine.script.validateNamespaceHashMap
import org.lain.engine.server.Notification
import org.lain.engine.server.account.DedicatedEngineAccountService
import org.lain.engine.transport.network.ConnectionAuthorization
import org.lain.engine.transport.network.ConnectionSession
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.transport.packet.CLIENTBOUND_VERIFICATION_ENDPOINT
import org.lain.engine.transport.packet.GeneralServerData
import org.lain.engine.transport.packet.SERVERBOUND_VERIFICATION_RESPONSE_ENDPOINT
import org.lain.engine.transport.packet.VerificationDataPacket
import org.lain.engine.transport.packet.VerificationResponsePacket

class AuthorizationListener(
    private val connectionManager: ServerConnectionManager,
    private val server: DedicatedEngineMinecraftServer,
    private val accountService: DedicatedEngineAccountService
) {
    private val supervisorJob = SupervisorJob()
    private val coroutineScope = CoroutineScope(server.engine.dataPrepareDispatcher + supervisorJob)

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

    fun onDisconnect(connection: ConnectionSession) {
        connection.cancelAuthorization()
    }

    fun stop() {
        connectionManager.iterateSessions().forEach { it.cancelAuthorization() }
        supervisorJob.cancel()
    }

    private fun onAuth(packet: AuthPacket, entity: ServerPlayer, id: PlayerId) {
        val connection = connectionManager.getSession(id)
        if (connection.authorization != null) {
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

        val engine = server.engine
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

        val verificationData = GeneralServerData(
            engine.globals.serverId,
            engine.globals.requireIdenticalNamespaces,
            entity.hasPermission(CHARACTER_PROVIDING_PERMISSION),
            engine.namespacedStorage.get().namespaceHashMap,
        )
        val verificationResponse = CompletableDeferred<VerificationResponsePacket>()
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            runAuthorization(connection, verificationData, mods.toSet(), verificationResponse)
        }
        val authorization = ConnectionAuthorization(verificationResponse, job)
        connection.authorization = authorization
        job.invokeOnCompletion {
            if (connection.authorization === authorization) {
                connection.authorization = null
            }
        }
        job.start()
    }

    private fun onVerificationResponse(packet: VerificationResponsePacket, playerId: PlayerId) {
        val connection = connectionManager.getSession(playerId)
        if (connection.authorization?.complete(packet) != true) {
            friendlyError("Ответ верификации получен до начала авторизации или повторно")
        }
    }

    private suspend fun runAuthorization(
        connection: ConnectionSession,
        verificationData: GeneralServerData,
        mods: Set<String>,
        verificationResponse: Deferred<VerificationResponsePacket>,
    ) {
        try {
            CLIENTBOUND_VERIFICATION_ENDPOINT.sendS2C(
                VerificationDataPacket(verificationData),
                connection.playerId,
            )
            finishAuthorization(connection, verificationData, mods, verificationResponse.await())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val currentSession = connectionManager.getSessionOrNull(connection.playerId)
            if (currentSession === connection) {
                connectionManager.disconnect(connection, e)
                if (e !is FriendlyException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun finishAuthorization(
        connection: ConnectionSession,
        verificationData: GeneralServerData,
        mods: Set<String>,
        response: VerificationResponsePacket,
    ) {
        val engine = server.engine
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
        if (mods.contains("freecam")) {
            notifyOperators("Freecam", connection.username)
            notifications += Notification.FREECAM
        }

        val settings = EngineMinecraftServer.serverMinecraftPlayerLoadSettings(
            engine,
            entity,
            connection.playerId,
            response.developerModeStatus,
            notifications,
            verificationData.canProvideCharacters
        )

        engine.playerLoader.loadPreparing(
            settings = settings,
            account = PlayerLoadSettings.Account(response.character),
            beforeCommit = { requireCurrentSession(connection) },
            onCreated = { connection.player = it }
        )
    }

    private fun requireCurrentSession(connection: ConnectionSession) {
        val currentSession = connectionManager.getSessionOrNull(connection.playerId)
        if (currentSession !== connection) {
            throw CancellationException("Сессия игрока изменилась во время авторизации")
        }
    }

    private fun ConnectionSession.cancelAuthorization() {
        authorization?.cancel()
        authorization = null
    }

    internal fun notifyOperators(id: String, username: Username) {
        server.minecraftServer.players
            .filter { it.isOp }
            .forEach { it.sendMessage("<red>[!]<reset><gold> $username зашел на сервер с $id</gold>") }
    }

    companion object {
        val MINIMAP_WARNING = "Использование мини-карт запрещено."
    }
}
