package org.lain.engine.player

import org.lain.engine.item.*
import org.lain.engine.util.*

sealed class Interaction {
    data class SlotClick(
        val cursorItem: EngineItem,
        val item: EngineItem,
    ) : Interaction()

    object RightClick : Interaction()
    object LeftClick : Interaction()
}

data class InteractionComponent(val interaction: Interaction) : Component

fun EnginePlayer.setInteraction(interaction: Interaction) {
    this.set(InteractionComponent(interaction))
}

/** @return Отменить стандартное взаимодействие */
fun processLeftClickInteraction(player: EnginePlayer, handItem: EngineItem? = player.handItem): Boolean {
    // стрельба
    return handItem?.has<Gun>() == true
}

fun updatePlayerInteractions(player: EnginePlayer) {
    val interaction = player.get<InteractionComponent>()?.interaction ?: return
    val handItem = player.handItem

    when(interaction) {
        Interaction.LeftClick -> {
            if (handItem?.has<Gun>() == true) {
                handItem.setGunEvent(GunEvent.Shoot(player))
            }
        }
        is Interaction.RightClick -> {
            // предохранитель
            if (handItem?.has<Gun>() == true) {
                handItem.setGunEvent(GunEvent.SelectorToggle(player))
            }
        }
        is Interaction.SlotClick -> {
            val slotItem = interaction.item
            val cursorItem = interaction.cursorItem

            // Кейс 1: объединение
            if (merge(slotItem, cursorItem)) {
                player.require<PlayerInventory>().handItem = null
            }

            // Кейс 2: загрузка боеприпасов в оружие
            slotItem.handle<Gun> {
                val count = slotItem.gunAmmoConsumeCount(cursorItem)
                if (count > 0) {
                    slotItem.setGunEvent(GunEvent.BarrelAmmoLoad(count, player))
                    player.set(DestroyItemSignal(cursorItem.uuid, count))
                }
            }
        }
    }

    player.remove<InteractionComponent>()
}