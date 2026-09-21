package org.lain.engine.player.character

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.data.PersistentCharacterSnapshot

@JvmInline
@Serializable
value class CharacterId(val value: String) {
    override fun toString(): String = value
}

@Serializable
data class EngineCharacter(
    val profile: CharacterProfile,
    val looks: List<Look>,
    val baseLook: Look,
) {
    val id get() = profile.id
}

@Serializable
data class CharacterProfile(
    val id: CharacterId,
    val accountId: String,
    val name: CharacterGradientName,
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

data class UsedCharacters(val characters: MutableSet<CharacterId>) : Component