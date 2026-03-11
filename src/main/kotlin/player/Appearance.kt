package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.item.*
import org.lain.engine.server.markDirty
import org.lain.engine.transport.packet.ItemComponent
import org.lain.engine.util.Color
import org.lain.engine.util.ContentStorage
import org.lain.engine.util.Storage
import org.lain.engine.util.component.*
import org.lain.engine.util.math.Vec2
import org.lain.engine.util.nextId
import org.lain.engine.world.location

/**
 * # Снаряжение
 * Игрок может цеплять на себя предметы с компонентом одежды. Они складываются в линейный список
 * и накладывают `layer` на части `parts`. Приоритетом является индекс компонента в массиве `outfits`
 */
@Serializable
data class Equipment(
    val outfits: MutableList<EquippedItem> = mutableListOf(),
) : Component {
    fun copy(): Equipment {
        return Equipment(outfits.toMutableList())
    }
}

// TODO: Заменить позже на нормальную систему инвентарей
@Serializable
data class EquippedItem(
    val outfit: Outfit,
    val model: ItemAssets,
    val name: String,
    val progressionAnimation: ProgressionAnimationId?,
    val itemId: ItemId,
)

val EnginePlayer.outfit
    get() = this.require<Equipment>().outfits

@Serializable
data class Outfit(
    val display: OutfitDisplay = OutfitDisplay.Separated,
    val parts: List<PlayerPart> = emptyList(),
    val purity: Purity = Purity(nextId()),
) : ItemComponent

@Serializable
sealed class OutfitDisplay {
    @Serializable
    object Separated : OutfitDisplay()
    @Serializable
    data class Texture(val layer: SkinLayerId) : OutfitDisplay()
}

@JvmInline
@Serializable
value class SkinLayerId(val path: String)

enum class PlayerPart {
    HEAD,
    LEFT_ARM,
    RIGHT_ARM,
    LEFT_PALM,
    RIGHT_PALM,
    BODY,
    LEFT_LEG,
    RIGHT_LEG,
    LEFT_FEET,
    RIGHT_FEET
}

/**
 * # Чистота
 * Одежда и кожа игрока подвержена влиянию окружения и персистентно хранит изменения: засохшую корвь, грязь, пыль, царапины и т.д.
 * Элементы также накладываются на скин, однако, их содержание определяется случайно, опираясь на сид.
 */
@Serializable
data class Purity(
    val seed: Long,
    val elements: List<SkinElement> = listOf(),
)

@Serializable
data class SkinElement(val x: Int, val y: Int) {
    @Serializable
    sealed class Contents {
        @Serializable
        data class Mud(val color: Color) : Contents()
        @Serializable
        data class Blood(val scale: Int) : Contents()
        @Serializable
        data class Scratch(val vector: Vec2) : Contents()
    }
}

data class PlayerPurity(val parts: MutableMap<PlayerPart, List<SkinElement>>) : Component

private val EQUIP_VERB = VerbType("equip", "Одеть")
private val TAKE_OFF_EQUIP_VERB = VerbType("take_off_equip", "Снять")
private val EQUIP_PROGRESSION_ANIMATION = "equip"
private val TAKE_OFF_PROGRESSION_ANIMATION = "take_off"

fun appendPlayerEquipmentVerbs(player: EnginePlayer) = player.handle<VerbLookup> {
    forAction<InputAction.Base> {
        EQUIP_VERB.takeIf { handItem != null && handItem.has<Outfit>() && handItem.has<ItemAssets>() }
    }
    forAction<InputAction.TakeOff>() {
        TAKE_OFF_EQUIP_VERB.takeIf { handItem == null && player.outfit.isNotEmpty() }
    }
}

context(contents: ContentStorage)
fun handlePlayerEquipmentInteraction(
    player: EnginePlayer,
    itemStorage: Storage<ItemUuid, EngineItem>
) {
    player.handleInteraction(EQUIP_VERB) {
        val handItem = handItem ?: return@handleInteraction
        val outfit = handItem.require<Outfit>()
        attachHandItemProgression(EQUIP_PROGRESSION_ANIMATION, 40)

        if (progressionFinished) {
            player.set(DestroyItemSignal(handItem.uuid))
            player.outfit += EquippedItem(
                outfit,
                handItem.require(),
                handItem.name,
                handItem.getProgressionAnimation(TAKE_OFF_PROGRESSION_ANIMATION),
                handItem.id
            )
            player.completeInteraction()
            player.markDirty<Equipment>(id)
        }
    }

    player.handleInteraction(TAKE_OFF_EQUIP_VERB) {
        val outfit = player.outfit
        attachSelection(
            "Снять экипировку",
            outfit.mapIndexed { index, item ->
                InteractionSelection.Variant(
                    index.toString(),
                    item.name,
                    item.model.default,
                    true
                )
            }
        )

        val variant = selectionVariant
        if (variant != null) {
            val idx = variant.id.toInt()
            val item = outfit[idx]
            attachProgression(item.progressionAnimation, 60)
            placeholders["lowercased_outfit_name"] = item.name.replaceFirstChar { it.lowercase() }

            if (progressionFinished) {
                val engineItem = createItem(player.location, contents.items[item.itemId] ?: error("Предмета экипировки не существует"), itemStorage)
                player.set(MoveItemSignal(engineItem.uuid, player.selectedSlot))
                outfit.removeAt(idx)
                player.completeInteraction()
                player.markDirty<Equipment>(id)
            }
        }

        if (handItem != null) {
            player.completeInteraction()
        }
    }
}