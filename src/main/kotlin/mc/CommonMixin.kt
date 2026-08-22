package org.lain.engine.mc

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.EnginePlayerModel
import org.lain.engine.player.Jump
import org.lain.engine.player.PlayerPhysics
import org.lain.engine.player.canJump
import org.lain.engine.player.flyingSpeed
import org.lain.engine.player.get
import org.lain.engine.player.jumpStrength
import org.lain.engine.player.require
import org.lain.engine.player.set
import org.lain.engine.player.speed
import org.lain.engine.util.lazyUntilNotNull
import kotlin.Boolean
import kotlin.Double
import kotlin.Float
import kotlin.let

object CommonMixin {
    class PlayerEntityAccess(private val entity: Player) {
        private val player: EnginePlayer? by lazyUntilNotNull { entity.getEngineState() }

        private fun playerOrNull() = player?.takeIf { !it.destroyed }

        fun getDisplayName(original: Component): Component {
            return playerOrNull()?.displayNameText ?: original
        }

        fun getSpeed(): Double {
            return playerOrNull()?.speed?.toDouble() ?: 0.1
        }

        fun getJumpStrength(): Double {
            return playerOrNull()?.jumpStrength?.toDouble() ?: 0.1
        }

        fun getScale(): Float {
            return playerOrNull()?.require<EnginePlayerModel>()?.scale ?: 1.0f
        }

        fun getFlyingSpeed(): Float {
            return playerOrNull()?.flyingSpeed ?: 1f
        }

        fun getNoPhysics(): Boolean? {
            return playerOrNull()?.get<PlayerPhysics>()?.noClip
        }

        fun onPlayerJump() = playerOrNull()?.set(Jump)

        fun canJump(): Boolean {
            return playerOrNull()?.let { it.canJump(it.simulationSettings.movementSettings) } ?: false
        }
    }

    fun onBlockItemPlaced(context: BlockPlaceContext, level: Level, blockPos: BlockPos, state: BlockState) {
        level.engineAccess()?.onBlockPlaced(context.player, blockPos, state, level)
    }

    fun onAirBlockPlaced(level: Level, pos: BlockPos) {
        level.engineAccess()?.onBlockDestroyed(level, pos)
    }

    fun onBlockInteraction(player: Player, level: Level, blockPos: BlockPos): Boolean {
        return level.engineAccess()?.onBlockInteraction(player, level, blockPos) == true
    }

}
