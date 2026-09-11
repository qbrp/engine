package org.lain.engine.mc

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.CustomPlayerAttributes
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.EnginePlayerModel
import org.lain.engine.player.PlayerPhysics
import org.lain.engine.player.attributes
import org.lain.engine.player.get
import org.lain.engine.player.require
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.SBool
import org.lain.engine.util.lazyUntilNotNull
import kotlin.Boolean
import kotlin.Double
import kotlin.Float
import kotlin.let

object CommonMixin {
    class PlayerEntityAccess(private val entity: Player) {
        private val player: EnginePlayer? by lazyUntilNotNull { entity.getEngineState() }

        private fun playerOrNull() = player?.takeIf { !it.destroyed }

        private fun attributesOrNull() = player?.attributes

        private fun customAttributes() = player?.get<CustomPlayerAttributes>()

        fun getDisplayName(original: Component): Component {
            return playerOrNull()?.displayNameText ?: original
        }

        fun getSpeed(): Float {
            return customAttributes()?.speed ?: attributesOrNull()?.speed ?: 0.1f
        }

        fun getJumpStrength(): Float {
            return customAttributes()?.jumpStrength ?: attributesOrNull()?.jumpStrength ?: 0.1f
        }

        fun getFlyingSpeed(): Float {
            return attributesOrNull()?.flySpeed ?: 1f
        }

        fun getScale(): Float {
            return playerOrNull()?.require<EnginePlayerModel>()?.scale ?: 1.0f
        }

        fun getNoPhysics(): Boolean? {
            return playerOrNull()?.get<PlayerPhysics>()?.noClip
        }

        fun onPlayerJump() {
            val player = playerOrNull() ?: return
            val world = player.world
            val jump = world.simulation.scriptEngine.createScriptComponent(
                SBool(true),
                CoreScriptComponents.PLAYER_JUMP
            )
            with(world) {
                player.entity.setComponent(jump, CoreScriptComponents.PLAYER_JUMP)
            }
        }

        fun canJump(): Boolean {
            return playerOrNull()?.attributes?.jumpStrength?.let { it > 0f } ?: true
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
