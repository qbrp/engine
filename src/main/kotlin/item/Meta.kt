package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.ProgressionAnimationId
import org.lain.engine.storage.PersistentId
import org.lain.engine.transport.packet.ItemComponent
import org.lain.engine.world.*

@Serializable
data class ItemSounds(val sounds: Map<String, SoundEventId>) : ItemComponent

context(world: World)
fun EngineItem.emitPlaySoundEvent(
    key: String,
    category: EngineSoundCategory = EngineSoundCategory.AMBIENT,
    volume: Float = 1f,
    pitch: Float = 1f,
    player: EnginePlayer? = null,
    context: SoundContext? = null
) {
    world.emitPlaySoundEvent(
        WorldSoundPlayRequest.Item(this, key, category, volume, pitch, player, context)
    )
}

@Serializable
data class ItemAssets(val assets: Map<String, String>) : ItemComponent {
    val default = assets["default"] ?: "missingno"
    fun copy() = ItemAssets(assets.toMap())

    companion object {
        fun withDefaultAsset(asset: String): ItemAssets {
            return ItemAssets(mapOf("default" to asset))
        }
    }
}

@Serializable
data class ItemProgressionAnimations(val animations: Map<String, ProgressionAnimationId>) : ItemComponent

context(world: World)
fun EngineItem.getDefaultModel() = this.requireComponent<ItemAssets>().default

context(world: World)
fun EngineItem.getProgressionAnimation(key: String): ProgressionAnimationId? {
    return this.getComponent<ItemProgressionAnimations>()?.animations[key]
}
@Serializable
data class ItemMeta(val uuid: PersistentId, val id: ItemId) : Component

@Serializable
object Item : Component