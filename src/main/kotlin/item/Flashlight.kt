package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.player.VerbType

@Serializable
data class Flashlight(var enabled: Boolean) : Component

val LIGHT_TOGGLE = VerbType(
    "light_toggle",
    "Переключить фонарик"
)

//fun appendFlashlightVerbs(player: EnginePlayer) = player.handle<VerbLookup>() {
//    if (player.handItem?.get<Flashlight>() == null) return@handle
//    forAction<InputAction.Base>(LIGHT_TOGGLE)
//}
//
//context(interaction: InteractionComponent)
//fun handleFlashlightInteractions(player: EnginePlayer) = player.handleInteraction(LIGHT_TOGGLE) {
//    val flashlight = ha
//    handItem?.apply<Flashlight>() {
//        enabled = !enabled
//        emitItemInteractionSoundEvent(handItem, CLICK_SOUND)
//        handItem.markDirty<Flashlight>(id)
//    }
//}