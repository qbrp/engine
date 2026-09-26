package org.lain.engine.mc

import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import org.lain.engine.mc.ecs.MinecraftPlayer
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerStorage
import org.lain.engine.player.get
import org.lain.engine.player.set

fun PlayerStorage.require(entity: Player) = require(entity.engineId)

fun PlayerStorage.get(entity: Player) = get(entity.engineId)

fun Player.requireEngineState(): EnginePlayer {
    return MinecraftAccessRegistry.requireEnginePlayer(this)
}

fun Player.getEngineState(): EnginePlayer? {
    return MinecraftAccessRegistry.getEnginePlayer(this)
}

internal fun EnginePlayer.bindMinecraftEntity(entity: Player): Boolean {
    val minecraftPlayer = get<MinecraftPlayer>()
    if (minecraftPlayer == null) {
        set(MinecraftPlayer(entity))
        return true
    }
    if (minecraftPlayer.entity === entity) return false

    minecraftPlayer.entity = entity
    return true
}

fun replacePlayerMinecraftState(entity: Player) {
    val minecraftAccess = entity.level().engineAccess() ?: return
    val enginePlayer = minecraftAccess.getEnginePlayer(entity) ?: return
    enginePlayer.bindMinecraftEntity(entity)
}

class PlayerEntityData(
    var speed: Float = 0.1f,
    var jumpStrength: Float = 0.1f,
    var flyingSpeed: Float = 1f,
    var scale: Float = 1f,
    var noPhysics: Boolean = false,
    var canJump: Boolean = true,
) {
    var initialized: Boolean = false

    @Volatile
    var displayName: Text? = null
        private set

    fun updateThreadSafe(displayName: Text?) {
        this.displayName = displayName
    }
}

class PlayerEntityAccess {
    val data = PlayerEntityData()
    private var jumped: Boolean = false

    fun getDisplayName(original: Component): Component {
        return data.displayName ?: original
    }

    fun getSpeed() = data.speed

    fun getJumpStrength() = data.jumpStrength

    fun getFlyingSpeed() = data.flyingSpeed

    fun getScale() = data.scale

    fun getNoPhysics(): Boolean? = if (data.initialized) data.noPhysics else null

    fun canJump() = data.canJump

    fun onJump() {
        jumped = true
    }

    fun consumeJump(): Boolean {
        if (!jumped) return false
        jumped = false
        return true
    }
}

val Player.enginePlayerAccess: PlayerEntityAccess
    get() = (this as PlayerEntityAccessHolder).`engine$getPlayerEntityAccess`()
