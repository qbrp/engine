package org.lain.engine.client.mc.compat

import org.lain.cyberia.ecs.iterate
import org.lain.engine.client.GameSession
import org.lain.engine.mc.compat.applyGenderConfig
import org.lain.engine.player.character.CharacterApplyEvent

fun GameSession.tickGenderSystem() = world.iterate<CharacterApplyEvent> { _, (character, playerId) ->
    val profile = character.profile
    val player = playerStorage.get(playerId) ?: return@iterate
    applyGenderConfig(player.id, profile.biologicalSex, profile.genderParams)
}