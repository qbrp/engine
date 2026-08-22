package org.lain.engine.client.render

import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.core.ClientAsset
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerComponent
import org.lain.engine.player.PlayerId
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.SelectedLook
import org.lain.engine.player.character.computeCharacterModel
import org.lain.engine.player.getOrSet
import org.lain.engine.player.require
import org.lain.engine.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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

fun EnginePlayer.getSkin() = require<EnginePlayerSkin>().skin

class SkinSystem(
    private val skinTextureManager: SkinTextureManager
) {
    private val cache = ConcurrentHashMap<UUID, PlayerSkin>()

    fun get(playerId: UUID) = cache[playerId]

    fun removePlayerFromCache(playerId: PlayerId) = cache.remove(playerId.value)

    fun tick(world: World) {
        world.iterate<PlayerComponent, EnginePlayerSkin, SelectedLook, AppliedCharacter> {
                _, (player), component, (look), (character) ->
            val texture = skinTextureManager.getOrDownloadTextureNullable(look) ?: component.skin.body

            val profile = character.profile
            val model = computeCharacterModel(
                profile.bodyType,
                profile.biologicalCategory,
                profile.biologicalSex,
            )

            val current = component.skin
            val changed = current.body != texture || current.model != model

            if (changed) {
                component.skin = CharacterSkin(texture, model)
            }

            val skin = component.skin
            val playerId = player.id.value

            if (changed) {
                cache[playerId] = skin
            } else {
                cache.putIfAbsent(playerId, skin)
            }
        }
    }
}