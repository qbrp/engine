package org.lain.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.network.ServerPlayerEntity
import org.lain.engine.mc.engineId
import org.lain.engine.mc.getPlayer
import org.lain.engine.mc.hasPermission
import org.lain.engine.mc.isOp
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.Username
import org.lain.engine.server.Notification
import org.lain.engine.server.network
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.network.ConnectionSession
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.transport.network.ServerNetworkTransport
import org.lain.engine.transport.network.SessionId
import org.lain.engine.util.registerMinecraftServer
import org.lain.engine.util.text.parseMiniMessage
import java.util.*

class DedicatedServerEngineMod : DedicatedServerModInitializer {
    override fun onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val dependencies = EngineMinecraftServerDependencies(server)

            registerMinecraftServer(
                DedicatedEngineMinecraftServer(dependencies)
            )
        }
    }
}


class DedicatedEngineMinecraftServer(
    dependencies: EngineMinecraftServerDependencies,
    val connectionManager: ServerConnectionManager = ServerConnectionManager(),
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
    val username: Username,
    val mods: List<String>
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
    }

    private fun onAuth(packet: AuthPacket, entity: ServerPlayerEntity, id: PlayerId) {
        val engine = server.engine
        val connection = connectionManager.getSession(id)
        val username = packet.username
        val mods = packet.mods

        val notifications = mutableListOf<Notification>()
        val minimapPermission = entity.isOp || entity.hasPermission("minimap")
        val hasMinimap = mods.contains("xaeroworldmap") || mods.contains("xaerominimap") || mods.contains("voxelmap") || mods.contains("journeymap")
        if (!minimapPermission && hasMinimap) {
            connectionManager.disconnect(
                connection,
                "<bold>Вы были исключены с сервера из-за мода на мини-карту</bold><newline>$MINIMAP_WARNING"
            )
        }
        if (mods.contains("freecam")) {
            notifyOperators("Freecam", username)
            notifications += Notification.FREECAM
        }

        val player = newAuthorizedPlayerInstance(connection, entity)
        coroutineScope.launch {
            prepareServerMinecraftPlayer(server, entity, player)
            engine.execute {
                engine.instantiatePlayer(player)
                notifications.forEach { engine.handler.onServerNotification(player, it, false) }
            }
        }
    }

    internal fun notifyOperators(id: String, username: Username) {
        server.minecraftServer.playerManager.playerList
            .filter { it.isOp }
            .forEach {
                it.sendMessage("<red>[!]<reset><gold> $username зашел на сервер с $id</gold>".parseMiniMessage())
            }
    }

    private fun newAuthorizedPlayerInstance(
        connectionSession: ConnectionSession,
        entity: ServerPlayerEntity,
    ): EnginePlayer {
        return serverMinecraftPlayerInstance(server, entity, connectionSession.playerId)
    }

    companion object {
        val MINIMAP_WARNING =
            """Моды на мини-карты запрещены, поскольку позволяют получать информацию о местонахождении игроков и построек. 
      |Это нарушает принцип мета-информации и невозможно по РП: ваш персонаж в теории не способен знать это.
    """.trimMargin()
    }
}