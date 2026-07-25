package org.lain.engine.client.mc.compat

import org.lain.cyberia.ecs.iterate
import org.lain.engine.client.GameSession
import org.lain.engine.mc.applyGenderConfig
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.CharacterApplyEvent
import org.lain.engine.player.require
import org.lain.engine.world.World

fun GameSession.tickGenderSystem() = world.iterate<CharacterApplyEvent> { _, (character, playerId) ->
    val profile = character.profile
    val player = playerStorage.get(playerId) ?: return@iterate
    applyGenderConfig(player.id, profile.biologicalSex, profile.genderParams)
}