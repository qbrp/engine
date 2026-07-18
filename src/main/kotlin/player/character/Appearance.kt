package org.lain.engine.player.character

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.PlayerModelType
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

/**
 * Информация о персонаже, что может обновляться с сервера
 */
@Serializable
data class CharacterDisplay(
    val id: String,
    val name: CharacterName,
    val biologicalCategory: BiologicalCategory,
    val appearanceDescription: String,
    val height: CharacterHeight,
    val genderParams: GenderParams,
) : Component

@Serializable
@JvmInline
value class CharacterHeight(val meters: Float)

@Serializable
data class CharacterName(
    val name: String,
    val firstColor: Color,
    val secondColor: Color? = null,
) {
    val gradientText: List<ColoredChar>
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
): PlayerModelType {
    fun default() = when(bodyType) {
        BodyType.BROAD -> PlayerModelType.WIDE
        BodyType.NORMAL -> PlayerModelType.WIDE
        BodyType.SLIM -> PlayerModelType.SLIM
    }

    return when (category) {
        BiologicalCategory.HUMAN, BiologicalCategory.HUMANLIKE_HUMANOID -> {
            when(sex) {
                BiologicalSex.MALE, BiologicalSex.OTHER -> default()
                BiologicalSex.FEMALE -> when(bodyType) {
                    BodyType.NORMAL, BodyType.SLIM ->  PlayerModelType.SLIM
                    BodyType.BROAD ->  PlayerModelType.WIDE
                }
            }
        }
        else -> default()
    }
}