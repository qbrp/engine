package org.lain.engine.player.character

import kotlinx.serialization.Serializable

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