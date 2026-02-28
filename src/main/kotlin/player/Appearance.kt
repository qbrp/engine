package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.item.ItemAssets
import org.lain.engine.transport.packet.ItemComponent
import org.lain.engine.util.*
import org.lain.engine.util.math.Vec2

/**
 * # Снаряжение
 * Игрок может цеплять на себя предметы с компонентом одежды. Они складываются в линейный список
 * и накладывают `layer` на части `parts`. Приоритетом является индекс компонента в массиве `outfits`
 */
@Serializable
data class Equipment(
    val outfits: MutableList<EquippedItem> = mutableListOf(),
) : Component

// TODO: Заменить позже на нормальную систему инвентарей
@Serializable
data class EquippedItem(val outfit: Outfit, val model: ItemAssets)

val EnginePlayer.outfit
    get() = this.require<Equipment>().outfits

@Serializable
data class Outfit(
    val display: OutfitDisplay,
    val parts: List<PlayerPart>,
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
private val EQUIP_PROGRESSION_ANIMATION = "equip"

fun appendPlayerEquipmentVerbs(player: EnginePlayer) = player.handle<VerbLookup> {
    if (handItem == null || !handItem.has<Outfit>() || !handItem.has<ItemAssets>()) return@handle
    forAction<InputAction.Base>(EQUIP_VERB)
}

context(contents: ContentStorage)
fun handlePlayerEquipmentInteraction(player: EnginePlayer) = player.handleInteraction(EQUIP_VERB) {
    val handItem = handItem ?: return@handleInteraction
    val outfit = handItem.require<Outfit>()
    attachHandItemProgression(EQUIP_PROGRESSION_ANIMATION, 40)

    if (progressionFinished) {
        player.set(DestroyItemSignal(handItem.uuid))
        player.outfit += EquippedItem(outfit, handItem.require())
        player.completeInteraction()
    }
}