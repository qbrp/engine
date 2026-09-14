package org.lain.engine.player.character

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.player.ColoredChar
import org.lain.engine.player.gradientText
import org.lain.engine.util.Color

@Serializable
data class GenderParams(
    val breastSize: Float,
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
    WIDE,
    SLIM,
}

/**
 * Информация о персонаже, что может обновляться с сервера
 */
@Serializable
data class CharacterDisplay(
    val id: String,
    val name: CharacterGradientName,
    val biologicalCategory: BiologicalCategory,
    val appearanceDescription: String,
    val height: CharacterHeight,
    val genderParams: GenderParams,
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

fun EngineCharacter.getDisplay(): CharacterDisplay {
    return CharacterDisplay(
        profile.id,
        profile.name,
        profile.biologicalCategory,
        profile.appearanceDescription,
        profile.height,
        profile.genderParams
    )
}

fun computeCharacterModel(
    bodyType: BodyType,
    category: BiologicalCategory,
    sex: BiologicalSex
): CharacterModelType {
    fun default() = when(bodyType) {
        BodyType.BROAD -> CharacterModelType.WIDE
        BodyType.NORMAL -> CharacterModelType.WIDE
        BodyType.SLIM -> CharacterModelType.SLIM
    }

    return when (category) {
        BiologicalCategory.HUMAN, BiologicalCategory.HUMANLIKE_HUMANOID -> {
            when(sex) {
                BiologicalSex.MALE, BiologicalSex.OTHER -> default()
                BiologicalSex.FEMALE -> when(bodyType) {
                    BodyType.NORMAL, BodyType.SLIM -> CharacterModelType.SLIM
                    BodyType.BROAD -> CharacterModelType.WIDE
                }
            }
        }
        else -> default()
    }
}
