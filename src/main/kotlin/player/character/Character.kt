package org.lain.engine.player.character

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.player.PlayerId

@JvmInline
@Serializable
value class BackendCharacterId(val value: String) {
    override fun toString(): String = value

    fun asServer() = ServerCharacterId(value)
}

@JvmInline
@Serializable
value class ServerCharacterId(val value: String) {
    override fun toString(): String = value
}

@Serializable
data class CharacterData(
    val profile: CharacterProfile,
    val looks: List<Look>,
    val baseLook: Look,
)

@Serializable
data class ServerCharacterData(
    val id: ServerCharacterId,
    val data: CharacterData
)

@Serializable
data class BackendCharacterData(
    val id: BackendCharacterId,
    val data: CharacterData
)

@Serializable
data class CharacterProfile(
    val name: CharacterGradientName,
    val biologicalCategory: BiologicalCategory,
    val biologicalSex: BiologicalSex,
    val appearanceDescription: String,
    val height: CharacterHeight,
    val bodyType: BodyType,
    val appearance: CharacterAppearance,
)

@Serializable
data class OwnedCharacterId(val owner: CharacterOwner, val character: ServerCharacterId) {
    fun stringRepresentation(): String = stringRepresentation(owner, character)

    companion object {
        fun stringRepresentation(owner: CharacterOwner, characterId: ServerCharacterId): String {
            val owner = when (owner) {
                is CharacterOwner.Player -> owner.id.toString()
                CharacterOwner.Server -> "server"
            }
            return "$owner-$characterId"
        }
        fun player(playerId: PlayerId, characterId: ServerCharacterId) = OwnedCharacterId(
            CharacterOwner.Player(playerId),
            characterId
        )
        fun server(characterId: ServerCharacterId) = OwnedCharacterId(
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
    val character: ServerCharacterData,
    val id: OwnedCharacterId
) : Component

data class UsedCharacters(val characters: MutableSet<OwnedCharacterId>) : Component