package org.lain.engine.client.render

import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.resources.ResourceLocation
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerComponent
import org.lain.engine.player.PlayerId
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.CharacterModelType
import org.lain.engine.player.character.SelectedLook
import org.lain.engine.player.character.computeCharacterModel
import org.lain.engine.player.get
import org.lain.engine.world.World
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class EnginePlayerSkin(
    var skin: PlayerSkin = DefaultPlayerSkin.get(UUID(0L, 0L)),
) : Component

fun CharacterSkin(texture: ResourceLocation, model: CharacterModelType) = PlayerSkin(
    texture,
    null,
    null,
    null,
    when (model) {
        CharacterModelType.WIDE -> PlayerSkin.Model.WIDE
        CharacterModelType.SLIM -> PlayerSkin.Model.SLIM
    },
    true
)

fun EnginePlayer.getSkin() = get<EnginePlayerSkin>()?.skin

class SkinSystem(
    private val skinTextureManager: SkinTextureManager
) {
    private val cache = ConcurrentHashMap<UUID, PlayerSkin>()

    fun get(playerId: UUID) = cache[playerId]

    fun removePlayerFromCache(playerId: PlayerId) = cache.remove(playerId.value)

    fun tick(world: World) {
        world.iterate<PlayerComponent, EnginePlayerSkin, SelectedLook, AppliedCharacter> {
                _, (player), component, (look), (character) ->
            val texture = skinTextureManager.getOrDownloadTextureNullable(look) ?: component.skin.texture

            val profile = character.profile
            val model = computeCharacterModel(
                profile.bodyType,
                profile.biologicalCategory,
                profile.biologicalSex,
            )

            val current = component.skin
            val minecraftModel = when (model) {
                CharacterModelType.WIDE -> PlayerSkin.Model.WIDE
                CharacterModelType.SLIM -> PlayerSkin.Model.SLIM
            }
            val changed = current.texture != texture || current.model != minecraftModel

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
