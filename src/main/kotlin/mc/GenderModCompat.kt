package org.lain.engine.mc

import com.wildfire.main.WildfireGender
import com.wildfire.main.config.enums.Gender
import com.wildfire.main.networking.WildfireSync
import net.minecraft.server.level.ServerPlayer
import org.jetbrains.exposed.v1.core.transactions.withThreadLocalTransaction
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.character.BiologicalSex
import org.lain.engine.player.character.CharacterPhysical
import org.lain.engine.player.character.CharacterProfile
import org.lain.engine.player.character.GenderParams
import org.lain.engine.util.isClassAvailable

val GENDER_MOD_AVAILABLE = isClassAvailable("com.wildfire.main.WildfireGender")

fun syncPlayerGenderConfig(player: ServerPlayer, sex: BiologicalSex, genderParams: GenderParams?) {
    val config = WildfireGender.getOrAddPlayerById(player.uuid)
    config.updateGender(sex.wildfireGender)
    genderParams?.let { config.updateBustSize(it.breastSize) }
    WildfireSync.sendToAllClients(player, config)
}

val BiologicalSex.wildfireGender
    get() = when(this) {
        BiologicalSex.MALE -> Gender.MALE
        BiologicalSex.FEMALE -> Gender.FEMALE
        BiologicalSex.OTHER -> Gender.OTHER
    }