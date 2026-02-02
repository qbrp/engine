package org.lain.engine.player

import org.lain.engine.item.EngineItem
import org.lain.engine.item.Gun
import org.lain.engine.item.GunEvent
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.ItemUuid
import org.lain.engine.item.count
import org.lain.engine.item.gunAmmoConsumeCount
import org.lain.engine.item.setGunEvent
import org.lain.engine.util.Component
import org.lain.engine.util.apply
import org.lain.engine.util.applyIfExists
import org.lain.engine.util.get
import org.lain.engine.util.has
import org.lain.engine.util.remove
import org.lain.engine.util.require
import org.lain.engine.util.set
import kotlin.math.abs

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
    if (handItem?.has<Gun>() == true) {
        handItem.setGunEvent(GunEvent.Shoot(player))
        return true
    }
    return false
}

fun updatePlayerInteractions(player: EnginePlayer) {
    val interaction = player.get<InteractionComponent>()?.interaction ?: return
    val handItem = player.handItem

    when(interaction) {
        Interaction.LeftClick -> {
            processLeftClickInteraction(player, handItem)
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

            // Кейс 1: загрузка боеприпасов в оружие
            slotItem.applyIfExists<Gun> {
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