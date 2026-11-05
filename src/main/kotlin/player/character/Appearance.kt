package org.lain.engine.player.character

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.player.ColoredChar
import org.lain.engine.player.gradientText
import org.lain.engine.util.Color

@Serializable
data class CharacterAppearance(
    val breastSize: Float,
    val eyeLevel: Int,
)

@Serializable
data class Skin(
    val url: String,
)

@Serializable
data class Look(
    val id: String,
    val title: String,
    val skin: Skin,
    val appearanceDescriptionAddon: String,
    val base: Boolean,
)

@Serializable
data class SelectedLook(val look: Look) : Component

enum class CharacterModelType {
    DEFAULT, SLIM,
}

/**
 * Информация о персонаже, что может обновляться с сервера
 */
@Serializable
data class CharacterDisplay(
    val name: CharacterGradientName,
    val biologicalCategory: BiologicalCategory,
    val appearanceDescription: String,
    val height: CharacterHeight,
    val appearance: CharacterAppearance,
) : Component

@Serializable
@JvmInline
value class CharacterHeight(val meters: Float)

@Serializable
data class CharacterGradientName(
    val name: String,
    val firstColor: Color,
    val secondColor: Color? = null,
) {
    val gradientChars: List<ColoredChar>
        get() = gradientText(name, firstColor, secondColor ?: firstColor)
}

fun CharacterData.getDisplay(): CharacterDisplay {
    return CharacterDisplay(
        profile.name,
        profile.biologicalCategory,
        profile.appearanceDescription,
        profile.height,
        profile.appearance
    )
}

val BodyType.model get() = when(this) {
    BodyType.BROAD -> CharacterModelType.DEFAULT
    BodyType.NORMAL -> CharacterModelType.DEFAULT
    BodyType.SLIM -> CharacterModelType.SLIM
}