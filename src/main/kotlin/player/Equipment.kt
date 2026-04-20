package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.container.*
import org.lain.engine.item.*
import org.lain.engine.script.ContentStorage
import org.lain.cyberia.ecs.*
import org.lain.cyberia.ecs.handle
import org.lain.cyberia.ecs.has
import org.lain.cyberia.ecs.require
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.world.World
import org.lain.engine.world.world

@Serializable
enum class EquipmentSlot(name: String, val slotId: SlotId = SlotId(name)) {
    CAP("cap"),
    WEST("west"),
    MASK("mask");

    companion object {
        val slotIds = entries.map { it.slotId }.toSet()

        fun ofSlot(slotId: SlotId): EquipmentSlot {
            return entries.first { it.slotId == slotId }
        }
    }
}

/**
 * # Снаряжение
 * Игрок может цеплять на себя предметы с компонентом одежды. Они складываются в линейный список
 * и накладывают `layer` на части `parts`
 */
@Serializable
data class Equipment(val containerId: EntityId) : Component

// Вешать на сущность контейнера
data class PlayerEquipment(val player: EnginePlayer) : Component

val EnginePlayer.equipmentContainer: EntityId
    get() = this.require<Equipment>().containerId

fun EnginePlayer.collectOwnedItems(world: World = this.world): List<EngineItem> {
    return world.collectContainedRecursive(equipmentContainer)
}

private val EQUIP_VERB = VerbType("equip", "Одеть")
private val TAKE_OFF_EQUIP_VERB = VerbType("take_off_equip", "Снять")
private val EQUIP_PROGRESSION_ANIMATION = "equip"
private val TAKE_OFF_PROGRESSION_ANIMATION = "take_off"

fun appendPlayerEquipmentVerbs(player: EnginePlayer) = player.handle<VerbLookup> {
    forAction<InputAction.Base> {
        EQUIP_VERB.takeIf { handItem != null && handItem.has<Outfit>() && handItem.has<ItemAssets>() }
    }
    forAction<InputAction.TakeOff>() {
        TAKE_OFF_EQUIP_VERB.takeIf { player.handFree && player.world.getContainerItems(player.equipmentContainer).isNotEmpty() }
    }
}

context(interaction: InteractionComponent)
fun handlePlayerEquipmentInteraction(
    player: EnginePlayer,
) {
    player.handleInteraction(EQUIP_VERB) {
        val handItem = handItem ?: return@handleInteraction
        val outfit = handItem.require<Outfit>()
        if (progressionFinished) {
            player.equipmentContainer.setComponent(AssignSlot(handItem, outfit.slot.slotId))
        }
    }

    player.handleInteraction(TAKE_OFF_EQUIP_VERB) {
        val variant = selectionVariant
        if (variant != null && progressionFinished) {
            val idx = variant.id.toInt()
            val container = player.equipmentContainer
            val equippedItems = player.world.getContainerItems(container)
            val item = equippedItems[idx]
            player.mainContainer.setComponent(AssignItem(item.uuid))
        }
    }
}

context(contents: ContentStorage, interaction: InteractionComponent)
fun handlePlayerEquipmentInteractionProgression(player: EnginePlayer) {
    player.handleInteraction(EQUIP_VERB) {
        attachHandItemProgression(EQUIP_PROGRESSION_ANIMATION, 40)
        if (progressionFinished) {
            complete()
        }
    }

    player.handleInteraction(TAKE_OFF_EQUIP_VERB) {
        if (!player.handFree) complete()
        val container = player.equipmentContainer
        val equippedItems = player.world.getContainerItems(container)
        attachSelection(
            "Снять экипировку",
            equippedItems.mapIndexed { index, item ->
                InteractionSelection.Variant(
                    index.toString(),
                    item.name,
                    item.defaultModel,
                    true
                )
            }
        )

        val variant = selectionVariant
        if (variant != null) {
            if (progressionFinished) {
                complete()
                return@handleInteraction
            }
            val idx = variant.id.toInt()
            val item = equippedItems[idx]
            attachProgression(item.getProgressionAnimation(TAKE_OFF_PROGRESSION_ANIMATION), 60)
            placeholders["lowercased_outfit_name"] = item.name.replaceFirstChar { it.lowercase() }
        }
    }
}