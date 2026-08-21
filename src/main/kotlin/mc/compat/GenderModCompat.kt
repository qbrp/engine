package org.lain.engine.mc.compat

import com.wildfire.main.WildfireGender
import com.wildfire.main.config.enums.Gender
import com.wildfire.main.entitydata.PlayerConfig
import com.wildfire.main.networking.WildfireSync
import net.minecraft.server.level.ServerPlayer
import org.lain.engine.mc.engineId
import org.lain.engine.player.PlayerId
import org.lain.engine.player.character.BiologicalSex
import org.lain.engine.player.character.GenderParams
import org.lain.engine.util.isClassAvailable

val GENDER_MOD_AVAILABLE = isClassAvailable("com.wildfire.main.WildfireGender")

fun syncPlayerGenderConfig(serverPlayer: ServerPlayer, sex: BiologicalSex, genderParams: GenderParams?) {
    val config = applyGenderConfig(serverPlayer.engineId, sex, genderParams)
    WildfireSync.sendToAllClients(serverPlayer, config)
}

fun applyGenderConfig(playerId: PlayerId, sex: BiologicalSex, genderParams: GenderParams?): PlayerConfig {
    val config = WildfireGender.getOrAddPlayerById(playerId.value)
    config.updateGender(sex.wildfireGender)
    genderParams?.let { config.updateBustSize(it.breastSize) }
    return config
}

val BiologicalSex.wildfireGender
    get() = when(this) {
        BiologicalSex.MALE -> Gender.MALE
        BiologicalSex.FEMALE -> Gender.FEMALE
        BiologicalSex.OTHER -> Gender.OTHER
    }