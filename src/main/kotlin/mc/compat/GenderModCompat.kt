package org.lain.engine.mc.compat

import com.wildfire.main.WildfireGender
import com.wildfire.main.Gender
import com.wildfire.main.entitydata.PlayerConfig
import com.wildfire.main.networking.WildfireSync
import net.minecraft.server.level.ServerPlayer
import org.lain.engine.mc.engineId
import org.lain.engine.player.PlayerId
import org.lain.engine.player.character.BiologicalSex
import org.lain.engine.player.character.CharacterAppearance
import org.lain.engine.player.character.CharacterProfile
import java.util.UUID

fun syncPlayerGenderConfig(serverPlayer: ServerPlayer, sex: BiologicalSex, appearance: CharacterAppearance?) {
    val config = applyGenderConfig(serverPlayer.engineId, sex, appearance)
    WildfireSync.sendToAllClients(serverPlayer, config)
}

fun applyGenderConfig(playerId: PlayerId, sex: BiologicalSex, appearance: CharacterAppearance?): PlayerConfig {
    val config = WildfireGender.getOrAddPlayerById(playerId.value)
    config.updateGender(sex.wildfireGender)
    appearance?.let { config.updateBustSize(it.breastSize) }
    return config
}

fun updateCharacterEntity(character: CharacterProfile, uuid: UUID) {
    WildfireGender.getOrAddPlayerById(uuid).apply {
        updateGender(character.biologicalSex.wildfireGender)
        updateBustSize(character.appearance.breastSize)
    }
}

fun removeGenderCacheEntry(uuid: UUID) {
    WildfireGender.PLAYER_CACHE.remove(uuid)
}

private val BiologicalSex.wildfireGender
    get() = when(this) {
        BiologicalSex.MALE -> Gender.MALE
        BiologicalSex.FEMALE -> Gender.FEMALE
        BiologicalSex.OTHER -> Gender.OTHER
    }
