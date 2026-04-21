package org.lain.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.crash.CrashException
import net.minecraft.util.crash.CrashReport
import org.lain.engine.mc.engineId
import org.lain.engine.mc.getPlayer
import org.lain.engine.mc.hasPermission
import org.lain.engine.mc.isOp
import org.lain.engine.player.PlayerId
import org.lain.engine.player.Username
import org.lain.engine.script.*
import org.lain.engine.script.lua.LuaContext
import org.lain.engine.script.lua.LuaDataStorage
import org.lain.engine.script.lua.LuaDependencies
import org.lain.engine.server.Notification
import org.lain.engine.server.ServerId
import org.lain.engine.server.network
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.network.ConnectionSession
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.transport.network.ServerNetworkTransport
import org.lain.engine.transport.network.SessionId
import org.lain.engine.transport.packet.*
import org.lain.engine.util.file.ENGINE_DIR
import org.lain.engine.util.file.loadOrCreateServerConfig
import org.lain.engine.util.registerMinecraftServer
import org.lain.engine.util.text.parseMiniMessage
import org.luaj.vm2.lib.jse.JsePlatform
import java.util.*

class SetupException(val exceptions: List<CompilationException>) : Exception()

class DedicatedServerEngineMod : DedicatedServerModInitializer {
    private lateinit var luaContext: LuaContext

    private fun createLuaContext(namespacedStorage: NamespacedStorage) = LuaContext(
        LuaDependencies(
            JsePlatform.standardGlobals(),
            namespacedStorage,
            ENGINE_DIR.scripts,
            LuaDataStorage()
        )
    )

    override fun onInitializeServer() {
        val namespacedStorage = NamespacedStorage()
        val config = loadOrCreateServerConfig()
        luaContext = createLuaContext(namespacedStorage)
        val compilationResult = setupContents(config.server, namespacedStorage)

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val dependencies = EngineMinecraftServerDependencies(server, luaContext, compilationResult, config, namespacedStorage)
            registerMinecraftServer(
                DedicatedEngineMinecraftServer(dependencies)
            )
        }
    }

    fun setupContents(serverId: ServerId, namespacedStorage: NamespacedStorage): CompilationResult {
        val scanner = Scanner(System.`in`)
        error@ while (true) {
            try {
                val result = compileContents(ENGINE_DIR.contents, getLuaEntrypointDir(serverId), luaContext)
                if (result.exceptions.isNotEmpty()) {
                    throw SetupException(result.exceptions)
                }
                return result
            } catch (e: Exception) {
                LOGGER.error("Не удалось скомпилировать ресурсы Engine!")

                if (e is SetupException) {
                    e.exceptions.forEach {
                        LOGGER.error(it.errorString)
                    }
                } else {
                    LOGGER.error(e.message)
                }

                LOGGER.info("Перекомпилировать заново? y - да, n - выключить сервер")
                while (true) {
                    when (scanner.nextLine().lowercase()) {
                        "y" -> {
                            luaContext = createLuaContext(namespacedStorage)
                            continue@error
                        }
                        "n" -> throw CrashException(
                            CrashReport("Engine compilation", e)
                        )
                        else -> LOGGER.warn("y - да, n - выключить сервер")
                    }
                }
            }
        }
    }
}

class DedicatedEngineMinecraftServer(
    dependencies: EngineMinecraftServerDependencies,
    override val connectionManager: ServerConnectionManager = ServerConnectionManager(dependencies.minecraftServer, dependencies.entityTable),
    override val transportContext: ServerTransportContext = ServerNetworkTransport(dependencies.minecraftServer, connectionManager, dependencies.playerStorage),
) : EngineMinecraftServer(dependencies) {
    val authorizationListener = ServerAuthorizationListener(
        connectionManager,
        this,
    )

    override fun tick() {
        for (player in engine.playerStorage.getAll()) {
            if (player.network.disconnect) {
                val entity = entityTable.getEntity(player.id) as? ServerPlayerEntity ?: continue
                connectionManager.disconnect(
                    connectionManager.getSession(player.id),
                    "Время ожидания подтверждения входа в игру истекло"
                )
                onLeavePlayer(entity)
            }
        }
        super.tick()
    }

    override fun run() {
        super.run()
        authorizationListener.run()
    }

    override fun onJoinPlayer(entity: ServerPlayerEntity) {
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

    override fun onLeavePlayer(entity: ServerPlayerEntity) {
        super.onLeavePlayer(entity)
        connectionManager.removeConnectionSession(entity.engineId)
    }
}

@Serializable
data class AuthPacket(
    val mods: List<String>,
    val version: String,
) : Packet

val SERVERBOUND_AUTH_ENDPOINT = Endpoint<AuthPacket>()

class ServerAuthorizationListener(
    private val connectionManager: ServerConnectionManager,
    private val server: DedicatedEngineMinecraftServer,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun run() {
        SERVERBOUND_AUTH_ENDPOINT.registerReceiver { ctx ->
            val packet = this
            val playerId = ctx.sender
            val entity = server.minecraftServer.getPlayer(playerId) ?: error("Игрок ${ctx.sender} не находится на сервере или не найден")
            onAuth(packet, entity, playerId)
        }
        SERVERBOUND_VERIFICATION_RESPONSE_ENDPOINT.registerReceiver { ctx ->
            val playerId = ctx.sender
            val entity = server.minecraftServer.getPlayer(playerId) ?: error("Игрок ${ctx.sender} не находится на сервере или не найден")
            onVerificationResponse(developerModeStatus, namespaces, entity, playerId)
        }
    }

    private fun onAuth(packet: AuthPacket, entity: ServerPlayerEntity, id: PlayerId) {
        val connection = connectionManager.getSession(id)
        if (packet.version !in SharedConstants.ALLOWED_VERSIONS) {
            val versionsText = if (SharedConstants.ALLOWED_VERSIONS.size == 1) {
                SharedConstants.ALLOWED_VERSIONS.first()
            } else {
                "одна из следующих: ${SharedConstants.ALLOWED_VERSIONS.map { "<newline>$it" }}"
            }
            error("Несовместимая версия. Установлена ${packet.version}, в то время как требуется $versionsText")
        }

        val mods = packet.mods
        val minimapPermission = entity.isOp || entity.hasPermission("minimap")
        val hasMinimap = mods.contains("xaeroworldmap") || mods.contains("xaerominimap") || mods.contains("voxelmap") || mods.contains("journeymap")
        if (!minimapPermission && hasMinimap) {
            connectionManager.disconnect(
                connection,
                "<bold>Вы были исключены с сервера из-за мода на мини-карту</bold><newline>$MINIMAP_WARNING"
            )
        }
        connection.mods = mods.toSet()

        val engine = server.engine
        server.entityTable.setEntity(entity, id)

        CLIENTBOUND_VERIFICATION_ENDPOINT.sendS2C(
            VerificationDataPacket(
                GeneralServerData(engine.globals.serverId)
            ),
            id
        )
    }

    private fun onVerificationResponse(developerModeStatus: DeveloperModeStatus, playerNamespaces: NamespaceHashMap, entity: ServerPlayerEntity, playerId: PlayerId) {
        val connection = connectionManager.getSession(playerId)
        val serverNamespacesHash = server.engine.namespacedStorage.namespaceHashMap
        if (playerNamespaces != serverNamespacesHash) {
            val missing = serverNamespacesHash.keys.filter { it !in playerNamespaces }
                .joinToString { it.value }
            val invalid = serverNamespacesHash.filter { (id, hash) -> (playerNamespaces[id] ?: return@filter false) != hash }
                .toList().joinToString { it.first.value }

            val errorString = StringBuilder("<bold>Скрипты сервера отличаются от ваших</bold>")
            if (missing.isNotEmpty()) errorString.append("<newline>Отсутствуют: $missing")
            if (invalid.isNotEmpty()) errorString.append("<newline>Отличаются: $invalid")
            error(errorString.toString())
        }

        val username = connection.username
        val notifications = mutableListOf<Notification>()
        if (connection.mods.contains("freecam")) {
            notifyOperators("Freecam", username)
            notifications += Notification.FREECAM
        }

        coroutineScope.launch {
            server.engine.playerLoader.loadPreparing(
                settings = serverMinecraftPlayerLoadSettings(entity, playerId, developerModeStatus, notifications),
                exceptionHandler = { connectionManager.disconnect(playerId, it) }
            )
        }
    }

    internal fun notifyOperators(id: String, username: Username) {
        server.minecraftServer.playerManager.playerList
            .filter { it.isOp }
            .forEach {
                it.sendMessage("<red>[!]<reset><gold> $username зашел на сервер с $id</gold>".parseMiniMessage())
            }
    }

    companion object {
        val MINIMAP_WARNING =
            """Моды на мини-карты запрещены, поскольку позволяют получать информацию о местонахождении игроков и построек. 
      |Это нарушает принцип мета-информации и невозможно по РП: ваш персонаж в теории не способен знать это.
    """.trimMargin()
    }
}