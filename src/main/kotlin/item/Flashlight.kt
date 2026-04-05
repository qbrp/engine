package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.player.*
import org.lain.engine.server.ItemSynchronizable
import org.lain.engine.server.markDirty
import org.lain.engine.transport.packet.ItemComponent
import org.lain.cyberia.ecs.apply
import org.lain.cyberia.ecs.get
import org.lain.cyberia.ecs.handle

@Serializable
data class Flashlight(
    var enabled: Boolean,
    val emitter: ConeLightEmitterSettings
) : ItemComponent, ItemSynchronizable

@Serializable
data class ConeLightEmitterSettings(
    val radius: Float = 8f,
    val distance: Float = 20f,
    val light: Float = 15f,
)

val EngineItem.lights: Boolean
    get() = this.get<Flashlight>()?.enabled == true

val LIGHT_TOGGLE = VerbType(
    "light_toggle",
    "Переключить фонарик"
)

fun appendFlashlightVerbs(player: EnginePlayer) = player.handle<VerbLookup>() {
    if (player.handItem?.get<Flashlight>() == null) return@handle
    forAction<InputAction.Base>(LIGHT_TOGGLE)
}

context(interaction: InteractionComponent)
fun handleFlashlightInteractions(player: EnginePlayer) = player.handleInteraction(LIGHT_TOGGLE) {
    handItem?.apply<Flashlight>() {
        enabled = !enabled
        emitItemInteractionSoundEvent(handItem, CLICK_SOUND)
        handItem.markDirty<Flashlight>(id)
    }
}