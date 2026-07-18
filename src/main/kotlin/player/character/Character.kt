package org.lain.engine.player.character

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.storage.ComponentDto

@Serializable
data class EngineCharacter(
    val profile: CharacterProfile,
    val looks: List<Look>,
    val baseLook: Look,
)

@Serializable
data class CharacterProfile(
    val id: String,
    val accountId: String,
    val name: CharacterName,
    val biologicalCategory: BiologicalCategory,
    val biologicalSex: BiologicalSex,
    val appearanceDescription: String,
    val height: CharacterHeight,
    val bodyType: BodyType,
    val genderParams: GenderParams,
)

////////////////////////////////////////////////////////

@Serializable
data class AppliedCharacter(val character: EngineCharacter) : Component

data class AppliedCharacters(val characters: MutableMap<String, State>) : Component {
    data class State(val components: List<ComponentDto>)
}