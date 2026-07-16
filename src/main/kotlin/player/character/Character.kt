package org.lain.engine.player.character

import org.lain.cyberia.ecs.Component

data class EngineCharacter(
    val profile: CharacterProfile,
    val looks: List<Look>,
)

data class CharacterProfile(
    val id: String,
    val accountId: String,
    val name: CharacterName,
    val biologicalCategory: BiologicalCategory,
    val biologicalSex: BiologicalSex,
    val appearanceDescription: String,
    val height: Double,
    val bodyType: BodyType,
    val genderParams: GenderParams,
)

////////////////////////////////////////////////////////

data class AppliedCharacters(val characters: Set<String>) : Component
