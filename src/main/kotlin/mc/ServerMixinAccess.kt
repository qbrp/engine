package org.lain.engine.mc

import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.GameMode as McGameMode
import org.lain.engine.player.Player
import org.lain.engine.player.displayName
import org.lain.engine.player.isSpectating
import org.lain.engine.player.jumpStrength
import org.lain.engine.player.speed
import org.lain.engine.util.injectMinecraftEngineServer
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.parseMiniMessage

object ServerMixinAccess {
    private val table by injectEntityTable()
    private val server by injectMinecraftEngineServer()
    private val chat get() = server.engine.chat
    private val chatSettings get() = chat.settings
    var isDamageEnabled = false

    fun getDisplayName(player: PlayerEntity): Text? {
        return player.engine?.displayName?.parseMiniMessage() ?: Text.literal("Загрузка имени...")
    }

    fun getSpeed(player: PlayerEntity): Double {
        return player.engine?.speed?.toDouble() ?: 0.1
    }

    fun getJumpStrength(player: PlayerEntity): Double {
        return player.engine?.jumpStrength?.toDouble() ?: 0.1
    }

    fun overrideSpectatorGameMode(player: PlayerEntity): McGameMode? {
        return if (player.engine?.isSpectating ?: return null) {
            McGameMode.SPECTATOR
        } else null
    }

    fun onBlockRemoved(world: World, blockState: BlockState, pos: BlockPos) {
        server.onBlockBreak(blockState, pos, world)
    }

    fun shouldCancelSendJoinMessage() = chatSettings.joinMessage != "" || !chatSettings.joinMessageEnabled

    fun shouldCancelSendLeaveMessage() = chat.settings.leaveMessage != "" || !chatSettings.leaveMessageEnabled

    fun shouldCancelDamage() = !isDamageEnabled

    private val PlayerEntity.engine: Player?
        get() = table.getPlayer(this)
}