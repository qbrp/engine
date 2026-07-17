package org.lain.engine.client.render

import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.core.ClientAsset
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.SelectedLook
import org.lain.engine.player.character.computeCharacterModel
import org.lain.engine.player.getOrSet
import org.lain.engine.player.require
import org.lain.engine.world.World

data class EnginePlayerSkin(
    var skin: PlayerSkin = DefaultPlayerSkin.getDefaultSkin(),
) : Component

fun CharacterSkin(texture: ClientAsset.Texture, model: PlayerModelType) = PlayerSkin(
    texture,
    null,
    null,
    model,
    true
)

fun EnginePlayer.getSkin() = getOrSet { EnginePlayerSkin() }.skin

fun World.tickSkinSystem(
    skinTextureManager: SkinTextureManager
) = iterate<EnginePlayerSkin, SelectedLook, AppliedCharacter>() { entity, enginePlayerSkin, (look), (character) ->
    val characterProfile = character.profile
    enginePlayerSkin.skin = CharacterSkin(
        skinTextureManager.getTexture(look),
        computeCharacterModel(
            characterProfile.bodyType,
            characterProfile.biologicalCategory,
            characterProfile.biologicalSex
        )
    )
}