package org.lain.engine.client.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CharacterNameDto(
    val name: String,
    val firstColor: String,
    val secondColor: String? = null,
)

@Serializable
data class GenderParamsDto(
    val breastSize: Float? = null,
)

@Serializable
data class CreateSkinRequest(
    val url: String,
)

@Serializable
data class CreateLookRequest(
    val title: String,
    val skin: CreateSkinRequest,
    val appearanceDescriptionAddon: String = "",
)

@Serializable
data class UpdateLookRequest(
    val title: String? = null,
    val skin: CreateSkinRequest? = null,
    val appearanceDescriptionAddon: String? = null,
)

@Serializable
data class CreateCharacterRequest(
    val name: CharacterNameDto,
    val biologicalCategory: BiologicalCategory,
    val biologicalSex: BiologicalSex,
    val appearanceDescription: String = "",
    val height: Double,
    val bodyType: BodyType,
    @SerialName("gender_params")
    val genderParams: GenderParamsDto = GenderParamsDto(),
    val baseSkin: CreateSkinRequest,
)

@Serializable
data class UpdateCharacterRequest(
    val name: CharacterNameDto? = null,
    val biologicalCategory: BiologicalCategory? = null,
    val biologicalSex: BiologicalSex? = null,
    val appearanceDescription: String? = null,
    val height: Double? = null,
    val bodyType: BodyType? = null,
    @SerialName("gender_params")
    val genderParams: GenderParamsDto? = null,
)

@Serializable
data class SkinResponse(
    val url: String,
)

@Serializable
data class LookResponse(
    val id: String,
    val title: String,
    val skin: SkinResponse,
    val appearanceDescriptionAddon: String,
    val base: Boolean,
)

@Serializable
data class LookSummaryResponse(
    val id: String,
    val title: String,
    val base: Boolean,
)

@Serializable
data class CharacterResponse(
    val id: String,
    val accountId: String,
    val name: CharacterNameDto,
    val biologicalCategory: BiologicalCategory,
    val biologicalSex: BiologicalSex,
    val appearanceDescription: String,
    val looks: List<LookSummaryResponse>,
    val height: Double,
    val bodyType: BodyType,
    @SerialName("gender_params")
    val genderParams: GenderParamsDto,
)

@Serializable
data class CharacterSummaryResponse(
    val id: String,
    val accountId: String,
    val name: CharacterNameDto,
    val biologicalCategory: BiologicalCategory,
    val biologicalSex: BiologicalSex,
    val appearanceDescription: String,
    val looks: List<LookSummaryResponse>,
    val height: Double,
    val bodyType: BodyType,
    @SerialName("gender_params")
    val genderParams: GenderParamsDto,
)
