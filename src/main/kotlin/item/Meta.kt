package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.ProgressionAnimationId
import org.lain.engine.transport.packet.ItemComponent
import org.lain.cyberia.ecs.get
import org.lain.cyberia.ecs.require
import org.lain.engine.world.*

@Serializable
data class ItemSounds(val sounds: Map<String, SoundEventId>) : ItemComponent

val EngineItem.sound
    get() = this.get<ItemSounds>()?.sounds

fun EngineItem.emitPlaySoundEvent(
    key: String,
    category: EngineSoundCategory = EngineSoundCategory.AMBIENT,
    volume: Float = 1f,
    pitch: Float = 1f,
    player: EnginePlayer? = null,
    context: SoundContext? = null
) {
    this.world.emitPlaySoundEvent(
        WorldSoundPlayRequest.Item(this, key, category, volume, pitch, player, context)
    )
}

@Serializable
data class ItemAssets(val assets: Map<String, String>) : ItemComponent {
    val default = assets["default"] ?: "missingno"
    fun copy() = ItemAssets(assets.toMap())
}

val EngineItem.defaultModel
    get() = this.require<ItemAssets>().default

@Serializable
data class ItemProgressionAnimations(val animations: Map<String, ProgressionAnimationId>) : ItemComponent

fun EngineItem.getProgressionAnimation(key: String): ProgressionAnimationId? {
    return this.get<ItemProgressionAnimations>()?.animations[key]
}