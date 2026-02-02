package org.lain.engine

import kotlinx.serialization.Serializable
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.network.ServerPlayerEntity
import org.lain.engine.mc.isOp
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.Username
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.network.ConnectionSession
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.transport.network.ServerNetworkTransport
import org.lain.engine.transport.network.SessionId
import org.lain.engine.util.engineId
import org.lain.engine.util.text.parseMiniMessage
import org.lain.engine.util.registerMinecraftServer
import java.util.UUID

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
    transportContext: ServerTransportContext = ServerNetworkTransport(connectionManager, dependencies.playerStorage),
) : EngineMinecraftServer(dependencies, transportContext) {
    val authorizationListener = ServerAuthorizationListener(
        connectionManager,
        this,
    )

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
    fun run() {
        SERVERBOUND_AUTH_ENDPOINT.registerReceiver { ctx -> onAuth(this, ctx.sender) }
    }

    private fun onAuth(packet: AuthPacket, id: PlayerId) {
        val connection = connectionManager.getSession(id)
        val username = packet.username
        val mods = packet.mods

        val warnings = mutableListOf<String>()
//        if (mods.contains("xaeroworldmap") || mods.contains("xaerominimap") || mods.contains("voxelmap")) {
//            connectionManager.disconnect(
//                connection,
//                "<bold>Вы были исключены с сервера из-за мода на мини-карту</bold><newline>$MINIMAP_WARNING"
//            )
//        }
        if (mods.contains("freecam")) {
            notifyOperators("Freecam", username)
            warnings.add(
                """<red>Вы используете мод <bold>Freecam</bold></red>
                |Его использование способствует получению мета-информации, для игры на сервере он запрещен.
            """
                    .trimMargin()
            )
        }

        val player = newAuthorizedPlayerInstance(connection, server.minecraftServer.playerManager.getPlayer(id.value)!!)
        server.engine.playerService.instantiate(player)
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
        return serverMinecraftPlayerInstance(
            server.engine,
            entity,
            connectionSession.playerId
        )
    }

    companion object {
        val MINIMAP_WARNING =
            """Моды на мини-карты запрещены, поскольку позволяют получать информацию о местонахождении игроков и построек. 
      |Это нарушает принцип мета-информации и невозможно по РП: ваш персонаж в теории не способен знать это.
    """.trimMargin()
    }
}