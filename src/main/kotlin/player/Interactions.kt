package org.lain.engine.player

import org.lain.engine.item.*
import org.lain.engine.server.ServerHandler
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

fun updatePlayerInteractions(player: EnginePlayer, removeInteraction: Boolean = true, handler: ServerHandler? = null) {
    val interaction = player.get<InteractionComponent>()?.interaction ?: return
    val handItem = player.handItem

    when(interaction) {
        Interaction.LeftClick -> {
            if (handItem?.has<Gun>() == true) {
                val rotationVector = player.require<Orientation>().rotationVector
                val start = player.eyePos
                handItem.setGunEvent(
                    GunEvent.Shoot(
                        GunShoot(start, rotationVector)
                    )
                )
            }
        }
        is Interaction.RightClick -> {
            // предохранитель
            if (handItem?.has<Gun>() == true) {
                handItem.setGunEvent(GunEvent.SelectorToggle)
            }
        }
        is Interaction.SlotClick -> {
            val slotItem = interaction.item
            val cursorItem = interaction.cursorItem

            // Кейс 1: объединение
            if (merge(slotItem, cursorItem)) {
                player.require<PlayerInventory>().mainHandItem = null
            }

            // Кейс 2: загрузка боеприпасов в оружие
            slotItem.handle<Gun> {
                val count = slotItem.gunAmmoConsumeCount(cursorItem)
                if (count > 0) {
                    slotItem.setGunEvent(GunEvent.BarrelAmmoLoad(count))
                    player.set(DestroyItemSignal(cursorItem.uuid, count))
                }
            }
        }
    }

    if (removeInteraction) player.remove<InteractionComponent>()
    handler?.onPlayerInteraction(player, interaction)
}