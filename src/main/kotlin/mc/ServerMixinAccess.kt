package org.lain.engine.mc

import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.GameMode as McGameMode
import org.lain.engine.player.Player
import org.lain.engine.player.displayName
import org.lain.engine.player.displayNameText
import org.lain.engine.player.isSpectating
import org.lain.engine.player.jumpStrength
import org.lain.engine.player.speed
import org.lain.engine.util.injectMinecraftEngineServer
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.text.displayNameMiniMessage
import org.lain.engine.util.text.parseMiniMessage
import org.lain.engine.util.text.parseMiniMessageLegacy

object ServerMixinAccess {
    private val table by injectEntityTable()
    private val server by injectMinecraftEngineServer()
    private val chat get() = server.engine.chat
    private val chatSettings get() = chat.settings
    var isDamageEnabled = false

    fun getDisplayName(player: PlayerEntity): Text? {
        return player.engine?.displayNameMiniMessage?.parseMiniMessageLegacy() ?: player.name
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

    fun onBlockAdded(world: World, blockPos: BlockPos, state: BlockState) {
        if (world.isClient) return
        server.onPlayerBlockInteraction(blockPos, state, world)
    }

    fun onBlockRemoved(world: World, blockState: BlockState, pos: BlockPos) {
        if (world.isClient) return
        server.onBlockBreak(blockState, pos, world)
    }

    fun shouldCancelSendJoinMessage() = chatSettings.joinMessage != "" || !chatSettings.joinMessageEnabled

    fun shouldCancelSendLeaveMessage() = chat.settings.leaveMessage != "" || !chatSettings.leaveMessageEnabled

    fun shouldCancelDamage() = !isDamageEnabled

    private val PlayerEntity.engine: Player?
        get() = table.getGeneralPlayer(this)
}