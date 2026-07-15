package org.lain.engine.player.character

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.player.ColoredChar
import org.lain.engine.player.account.CharacterData
import org.lain.engine.player.account.CharacterNameDto
import org.lain.engine.player.account.GenderParamsDto
import org.lain.engine.player.account.LookResponse
import org.lain.engine.player.gradientText
import org.lain.engine.util.Color
import org.lain.engine.util.math.parseHexColor

@Serializable
enum class BiologicalCategory(val displayName: String) {
    HUMAN("Человек"),
    HUMANLIKE_HUMANOID("Человекоподобный гуманоид"),
    HUMANOID("Гуманоид"),
    CREATURE("Существо"),
}

@Serializable
enum class BiologicalSex(val displayName: String) {
    MALE("Мужской"),
    FEMALE("Женский"),
    OTHER("Другой"),
}

@Serializable
enum class BodyType(val displayName: String) {
    BROAD("Широкое"),
    NORMAL("Обычное"),
    SLIM("Узкое"),
}

data class GenderParams(
    val breastSize: Float,
)

data class Skin(
    val url: String,
)

data class Look(
    val id: String,
    val title: String,
    val skin: Skin,
    val appearanceDescriptionAddon: String,
    val base: Boolean,
)

/**
 * Информация о персонаже, что может обновляться с сервера
 */
data class CharacterDisplay(
    val id: String,
    val name: CharacterName,
    val biologicalCategory: BiologicalCategory,
    val appearanceDescription: String,
    val height: Double,
    val genderParams: GenderParams,
) : Component

data class CharacterName(
    val name: String,
    val firstColor: Color,
    val secondColor: Color? = null,
) {
    val gradientText: List<ColoredChar>
        get() = gradientText(name, firstColor, secondColor ?: firstColor)
}

fun CharacterNameDto.map(): CharacterName = CharacterName(
    name,
    Color(parseHexColor(firstColor)),
    secondColor?.let { Color(parseHexColor(it)) }
)

fun GenderParamsDto.map(): GenderParams = GenderParams(breastSize ?: 0f)

fun CharacterData.getDisplay(): CharacterDisplay {
    return CharacterDisplay(
        profile.id,
        profile.name.map(),
        profile.biologicalCategory,
        profile.appearanceDescription,
        profile.height,
        profile.genderParams.map()
    )
}

fun CharacterData.getPhysical(): CharacterPhysical {
    return CharacterPhysical(
        profile.bodyType,
        profile.biologicalSex
    )
}

data class CharacterPhysical(
    val bodyType: BodyType,
    val sex: BiologicalSex
) : Component

////////////////////////////////////////////////////////

data class AppliedCharacters(val characters: Set<String>) : Component