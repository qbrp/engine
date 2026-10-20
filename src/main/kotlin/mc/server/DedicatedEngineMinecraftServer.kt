package org.lain.engine.mc.server

import net.minecraft.server.level.ServerPlayer
import org.lain.engine.mc.DisconnectText
import org.lain.engine.mc.engineId
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Username
import org.lain.engine.player.character.CharacterId
import org.lain.engine.player.character.BackendCharacter
import org.lain.engine.player.character.CharacterChangeRequest
import org.lain.engine.player.character.EngineBackend
import org.lain.engine.player.get
import org.lain.engine.server.replication.PlayerInstantiationConfirmation
import org.lain.engine.server.account.DedicatedEngineAccountService
import org.lain.engine.server.account.SessionTicket
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.network.ConnectionSession
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.transport.network.ServerNetworkTransport


class DedicatedEngineMinecraftServer(
    dependencies: Dependencies,
    override val connectionManager: ServerConnectionManager = ServerConnectionManager(),
    override val transportContext: ServerTransportContext = ServerNetworkTransport(
        dependencies.minecraftServer,
        connectionManager
    ),
) : EngineMinecraftServer(dependencies), EngineBackend {
    private val accountService = DedicatedEngineAccountService()
    val authorizationListener = AuthorizationListener(
        connectionManager,
        this,
        accountService
    )

    override val backend: EngineBackend
        get() = this

    override fun tick() {
        for (player in engine.playerStorage.all) {
            val instantiationConfirmation = player.get<PlayerInstantiationConfirmation>()
            if (instantiationConfirmation != null) {
                instantiationConfirmation.timeout -= 1
                if (instantiationConfirmation.timeout <= 0) {
                    engine.execute {
                        connectionManager.disconnect(
                            connectionManager.getSession(player.id),
                            "Время ожидания подтверждения входа в игру истекло"
                        )
                    }
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
        if (!connectionManager.addConnectionSession(
                ConnectionSession(
                    Username(entity.name.string),
                    entity.engineId,
                    entity.connection
                )
            )
        ) {
            entity.connection.disconnect(DisconnectText("Чет дохуя ты разошелся"))
            return
        }
        super.onJoinPlayer(entity)
    }

    override fun onLeavePlayer(entity: ServerPlayer) {
        val connection = connectionManager.removeConnectionSession(entity.connection) ?: return
        authorizationListener.onDisconnect(connection)
        super.onLeavePlayer(entity)
    }

    override suspend fun validateCharacterChange(
        player: EnginePlayer,
        request: CharacterChangeRequest
    ): BackendCharacter {
        require(request is CharacterChangeRequest.ResolveByTicket) {
            "Access denied"
        }

        return accountService
            .getAuthorized(request.ticket.map())
            .getCharacter(request.characterId)
            .map()
    }
}
