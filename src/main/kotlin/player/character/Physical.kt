package org.lain.engine.player.character

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component

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

fun EngineCharacter.getPhysical(): CharacterPhysical {
    return CharacterPhysical(
        profile.bodyType,
        profile.biologicalSex
    )
}

data class CharacterPhysical(
    val bodyType: BodyType,
    val sex: BiologicalSex
) : Component
