package org.lain.engine.util.file

import kotlinx.serialization.Serializable
import org.lain.engine.player.account.CharacterNameDto
import org.lain.engine.player.character.*

typealias BackendLook = Look

@Serializable
data class FileProvidedCharacter(
    val profile: Profile,
    val looks: Looks,
) {
    @Serializable
    data class Looks(
        val base: Look,
        val other: Map<String, Look> = emptyMap()
    )

    @Serializable
    data class Look(
        val title: String,
        val skin: Skin,
        val description: String,
    )

    @Serializable
    data class Profile(
        val name: CharacterNameDto,
        val biologicalCategory: BiologicalCategory,
        val biologicalSex: BiologicalSex,
        val appearanceDescription: String,
        val height: CharacterHeight,
        val bodyType: BodyType,
        val appearance: CharacterAppearance,
    )

    fun map(filename: String): CharacterData {
        val baseLook = looks.base.map("$filename-base", true)
        return CharacterData(
            CharacterProfile(
                profile.name.map(),
                profile.biologicalCategory,
                profile.biologicalSex,
                profile.appearanceDescription,
                profile.height,
                profile.bodyType,
                profile.appearance,
            ),
            looks.other.map { (lookId, look) -> look.map("$filename-$lookId", false) } + baseLook,
            baseLook
        )
    }

    private fun Look.map(id: String, base: Boolean) =
        BackendLook(id, title,skin, description, base)
}