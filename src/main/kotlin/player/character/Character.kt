package org.lain.engine.player.character

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.player.PlayerId

@JvmInline
@Serializable
value class CharacterId(val value: String) {
    override fun toString(): String = value
}

@Serializable
data class BackendCharacter(
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
    val appearance: CharacterAppearance,
)

@Serializable
data class PlayCharacterId(val owner: CharacterOwner, val character: CharacterId) {
    companion object {
        fun player(playerId: PlayerId, characterId: CharacterId) = PlayCharacterId(
            CharacterOwner.Player(playerId),
            characterId
        )
        fun server(characterId: CharacterId) = PlayCharacterId(
            CharacterOwner.Server,
            characterId
        )
    }
}

@Serializable
sealed class CharacterOwner {
    @Serializable
    @SerialName("server")
    object Server : CharacterOwner()
    @Serializable
    @SerialName("player")
    data class Player(val id: PlayerId) : CharacterOwner()
}

////////////////////////////////////////////////////////

@Serializable
data class AppliedCharacter(
    val character: BackendCharacter,
    val id: PlayCharacterId
) : Component

data class UsedCharacters(val characters: MutableSet<PlayCharacterId>) : Component