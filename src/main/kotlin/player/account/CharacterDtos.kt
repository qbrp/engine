package org.lain.engine.player.account

import kotlinx.serialization.Serializable
import org.lain.engine.player.character.BackendCharacterData
import org.lain.engine.player.character.BiologicalCategory
import org.lain.engine.player.character.BiologicalSex
import org.lain.engine.player.character.BodyType
import org.lain.engine.player.character.CharacterHeight
import org.lain.engine.player.character.ServerCharacterData
import org.lain.engine.player.character.CharacterAppearance
import org.lain.engine.player.character.CharacterData
import org.lain.engine.player.character.CharacterGradientName
import org.lain.engine.player.character.BackendCharacterId
import org.lain.engine.player.character.CharacterProfile
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
data class CharacterAppearanceDto(
    val breastSize: Float? = null,
    val eyeLevel: Int? = null
) {
    fun map(): CharacterAppearance = CharacterAppearance(breastSize ?: 0f, eyeLevel ?: 2)
}

@Serializable
data class CharacterDataResponse(
    val id: BackendCharacterId,
    val profile: CharacterProfileResponse,
    val looks: List<Look>,
) {
    fun map() = BackendCharacterData(
        id,
        CharacterData(
            profile.map(),
            looks,
            looks.firstOrNull { it.base } ?: looks.firstOrNull()
            ?: error("Невалидный ответ сервера: персонаж не имеет обликов")
        )
    )
}

@Serializable
data class CharacterProfileResponse(
    val id: BackendCharacterId, //TODO: unused
    val accountId: String,
    val name: CharacterNameDto,
    val biologicalCategory: BiologicalCategory,
    val biologicalSex: BiologicalSex,
    val appearanceDescription: String,
    val height: Double,
    val bodyType: BodyType,
    val appearance: CharacterAppearanceDto,
) {
    fun map() = CharacterProfile(
        name.map(),
        biologicalCategory,
        biologicalSex,
        appearanceDescription,
        CharacterHeight(height.toFloat()),
        bodyType,
        appearance.map()
    )
}