package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.transport.packet.ItemComponent
import org.lain.engine.util.Color
import org.lain.engine.util.component.Component
import org.lain.engine.util.math.Vec2
import org.lain.engine.util.nextId

@Serializable
data class Outfit(
    val slot: EquipmentSlot = EquipmentSlot.CAP,
    val display: OutfitDisplay = OutfitDisplay.Separated,
    val parts: List<PlayerPart> = emptyList(),
    val purity: Purity = Purity(nextId()),
    val dependsEyeY: Boolean = false,
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