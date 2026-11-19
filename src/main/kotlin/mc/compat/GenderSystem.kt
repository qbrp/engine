package org.lain.engine.mc.compat

import com.wildfire.main.WildfireGender
import net.minecraft.server.level.ServerPlayer
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.engine.Constants.GENDER_MOD_AVAILABLE
import org.lain.engine.mc.ecs.MinecraftPlayer
import org.lain.engine.player.EnginePlayerModel
import org.lain.engine.player.PlayerComponent
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.BiologicalSex
import org.lain.engine.world.World

fun World.tickGenderSystem() {
    if (!GENDER_MOD_AVAILABLE) return

    iterate<MinecraftPlayer, PlayerComponent, EnginePlayerModel> { entity, minecraft, (player), model ->
        val serverPlayer = if (isClient) {
            null
        } else {
            minecraft.entity as? ServerPlayer ?: return@iterate
        }
        val sex = entity.getComponent<AppliedCharacter>()?.character?.data?.profile?.biologicalSex
            ?: BiologicalSex.MALE
        val currentGender = WildfireGender.getPlayerById(player.id.value)?.gender
        if (model.lastBreastsSize == model.breastsSize && currentGender == sex.wildfireGender) return@iterate

        if (serverPlayer != null) {
            syncPlayerGenderConfig(serverPlayer, sex, model.breastsSize)
        } else {
            applyGenderConfig(player.id, sex, model.breastsSize)
        }
        model.lastBreastsSize = model.breastsSize
    }
}
