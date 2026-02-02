package org.lain.engine.client

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.packet.FullPlayerData

interface ClientEventBus {
    fun tick()
    fun onFullPlayerData(client: EngineClient, id: PlayerId, data: FullPlayerData)
    fun onPlayerDestroy(client: EngineClient, playerId: PlayerId)
    fun onMainPlayerInstantiated(client: EngineClient, gameSession: GameSession, player: EnginePlayer)
}