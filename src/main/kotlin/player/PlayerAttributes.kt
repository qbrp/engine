package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.server.markDirty
import org.lain.engine.util.Component
import org.lain.engine.util.require

@Serializable
data class AttributeValue(var default: Float, var custom: Float? = null) {
    fun get() = custom ?: default

    fun resetCustom() {
        custom = null
    }
}

@Serializable
data class PlayerAttributes(
    val speed: AttributeValue = AttributeValue(0.055f),
    var jumpStrength: AttributeValue = AttributeValue(0.37f),
    var gravity: AttributeValue = AttributeValue(0.055f),
) : Component {
    companion object {
        val DEFAULT = PlayerAttributes()
    }
}

@Serializable
data class MovementDefaultAttributes(
    val attributes: Map<PlayerStatus, Map<PrimaryAttribute, Float>> = mapOf()
) {
    fun getPrimarySeed(player: EnginePlayer): Float? {
        return attributes[player.status]?.get(PrimaryAttribute.SPEED)
    }

    fun getPrimaryJumpStrength(player: EnginePlayer): Float? {
        return attributes[player.status]?.get(PrimaryAttribute.JUMP_STRENGTH)
    }

    private val EnginePlayer.status: PlayerStatus
        get() = when {
            isInGameMasterMode -> PlayerStatus.GM
            isSpectating -> PlayerStatus.SPECTATING
            else -> PlayerStatus.DEFAULT
        }

    companion object {
        val BUILTIN = MovementDefaultAttributes(
            attributes = mapOf(
                PlayerStatus.DEFAULT to mapOf(
                    PrimaryAttribute.SPEED to 0.055f,
                    PrimaryAttribute.JUMP_STRENGTH to 0.4f
                ),
                PlayerStatus.GM to mapOf(
                    PrimaryAttribute.SPEED to 0.12f,
                    PrimaryAttribute.JUMP_STRENGTH to 0.45f
                ),
                PlayerStatus.SPECTATING to mapOf(
                    PrimaryAttribute.SPEED to 0.15f,
                )
            )
        )
    }
}

enum class PrimaryAttribute {
    SPEED, JUMP_STRENGTH
}

enum class PlayerStatus {
    DEFAULT, GM, SPECTATING
}

val EnginePlayer.attributes
    get() = this.require<PlayerAttributes>()

val EnginePlayer.speed: Float
    get() = attributes.speed.get()

fun EnginePlayer.setCustomGravity(gravity: Float) {
    attributes.gravity.custom = gravity
}

fun EnginePlayer.resetCustomGravity() {
    attributes.gravity.resetCustom()
}

fun EnginePlayer.setCustomSpeed(speed: Float) {
    attributes.speed.custom = speed
    markDirty<PlayerAttributes>()
}

fun EnginePlayer.resetCustomSpeed() {
    attributes.speed.resetCustom()
    markDirty<PlayerAttributes>()
}

val EnginePlayer.jumpStrength: Float
    get() = attributes.jumpStrength.get()

fun EnginePlayer.setCustomJumpStrength(value: Float) {
    attributes.jumpStrength.custom = value
    markDirty<PlayerAttributes>()
}

fun EnginePlayer.resetCustomJumpStrength() {
    attributes.jumpStrength.resetCustom()
    markDirty<PlayerAttributes>()
}
