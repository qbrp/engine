package org.lain.engine.player.account

import kotlinx.serialization.Serializable
import org.lain.engine.player.character.BiologicalCategory
import org.lain.engine.player.character.BiologicalSex
import org.lain.engine.player.character.BodyType
import org.lain.engine.player.character.CharacterHeight
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.character.CharacterGradientName
import org.lain.engine.player.character.CharacterProfile
import org.lain.engine.player.character.GenderParams
import org.lain.engine.player.character.Look
import org.lain.engine.util.Color
import org.lain.engine.util.math.parseHexColor

@Serializable
data class CharacterNameDto(
    val name: String,
    val firstColor: String,
    val secondColor: String? = null,
) {
    fun map(): CharacterGradientName = CharacterGradientName(
        name,
        Color(parseHexColor(firstColor)),
        secondColor?.let { Color(parseHexColor(it)) }
    )
}

@Serializable
data class GenderParamsDto(
    val breastSize: Float? = null,
) {
    fun map(): GenderParams = GenderParams(breastSize ?: 0f)
}

@Serializable
data class CharacterDataResponse(
    val profile: CharacterProfileResponse,
    val looks: List<Look>,
) {
    fun map() = EngineCharacter(
        profile.map(),
        looks,
        looks.firstOrNull { it.base } ?: looks.firstOrNull() ?: error("Невалидный ответ сервера: персонаж не имеет обликов")
    )
}

@Serializable
data class CharacterProfileResponse(
    val id: String,
    val accountId: String,
    val name: CharacterNameDto,
    val biologicalCategory: BiologicalCategory,
    val biologicalSex: BiologicalSex,
    val appearanceDescription: String,
    val height: Double,
    val bodyType: BodyType,
    val genderParams: GenderParamsDto,
) {
    fun map() = CharacterProfile(
        id,
        accountId,
        name.map(),
        biologicalCategory,
        biologicalSex,
        appearanceDescription,
        CharacterHeight(height.toFloat()),
        bodyType,
        genderParams.map()
    )
}