package org.lain.engine.mc.server

import net.minecraft.server.level.ServerPlayer
import org.lain.engine.mc.engineId
import org.lain.engine.mc.isOp
import org.lain.engine.mc.ecs.minecraftEntityNullable
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Username
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.get
import org.lain.engine.server.PlayerInstantiationConfirmation
import org.lain.engine.server.account.DedicatedEngineAccountService
import org.lain.engine.server.account.SessionTicket
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.network.ConnectionSession
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.transport.network.ServerNetworkTransport
import org.lain.engine.transport.network.SessionId
import java.util.UUID


class DedicatedEngineMinecraftServer(
    dependencies: Dependencies,
    override val connectionManager: ServerConnectionManager = ServerConnectionManager(dependencies.minecraftServer),
    override val transportContext: ServerTransportContext = ServerNetworkTransport(
        dependencies.minecraftServer,
        connectionManager
    ),
) : EngineMinecraftServer(dependencies) {
    private val accountService = DedicatedEngineAccountService()
    val authorizationListener = AuthorizationListener(
        connectionManager,
        this,
        accountService
    )

    override fun tick() {
        for (player in engine.playerStorage.all) {
            val instantiationConfirmation = player.get<PlayerInstantiationConfirmation>()
            if (instantiationConfirmation != null) {
                instantiationConfirmation.timeout -= 1
                if (instantiationConfirmation.timeout <= 0) {
                    val entity = player.minecraftEntityNullable as? ServerPlayer ?: continue
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
                entity.isOp,
                entity.connection
            )
        )
        super.onJoinPlayer(entity)
    }

    override fun onLeavePlayer(entity: ServerPlayer) {
        val engineId = entity.engineId
        connectionManager.getSessionOrNull(engineId)
            ?.let { authorizationListener.onDisconnect(it.uuid) }
        super.onLeavePlayer(entity)
        connectionManager.removeConnectionSession(engineId)
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