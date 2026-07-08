package org.lain.engine.client.account

import kotlinx.serialization.Serializable
import org.lain.engine.util.Color

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

data class CharacterName(
    val name: String,
    val firstColor: Color,
    val secondColor: Color?,
)

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

data class LookSummary(
    val id: String,
    val title: String,
    val base: Boolean,
)

data class CharacterProfile(
    val id: String,
    val authorId: String,
    val name: CharacterName,
    val biologicalCategory: BiologicalCategory,
    val biologicalSex: BiologicalSex,
    val appearanceDescription: String,
    val height: Double,
    val bodyType: BodyType,
    val genderParams: GenderParams,
)

data class CharacterSummary(
    val id: String,
    val authorId: String,
    val name: CharacterName,
    val biologicalCategory: BiologicalCategory,
    val biologicalSex: BiologicalSex,
    val appearanceDescription: String,
    val looks: List<LookSummary>,
    val height: Double,
    val bodyType: BodyType,
    val genderParams: GenderParams,
)
