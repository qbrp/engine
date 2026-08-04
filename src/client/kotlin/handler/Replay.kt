package org.lain.engine.client.handler

import org.lain.engine.player.PlayerId
import org.lain.engine.storage.EntityDto

data class EngineReplayState(
    val items: List<EntityDto>,
    val players: List<Player>,
) {
    data class Player(
        val id: PlayerId,
        val dto: EntityDto
    )
}