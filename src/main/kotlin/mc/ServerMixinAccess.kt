package org.lain.engine.mc

import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.chunk.WorldChunk
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.jumpStrength
import org.lain.engine.player.speed
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.injectMinecraftEngineServer
import org.lain.engine.util.text.displayNameMiniMessage
import org.lain.engine.util.text.parseMiniMessageLegacy

object ServerMixinAccess {
    private val table by injectEntityTable()
    private val server by injectMinecraftEngineServer()
    private val chat get() = server.engine.chat
    private val chatSettings get() = chat.settings
    var disableAchievementMessages = false
    var isDamageEnabled = false
    var blockRemovedCallback: ((WorldChunk, BlockPos) -> Unit)? = null

    fun isAchievementMessagesDisabled() = disableAchievementMessages

    fun getDisplayName(player: PlayerEntity): Text? {
        return player.engine?.displayNameMiniMessage?.parseMiniMessageLegacy() ?: player.name
    }

    fun getSpeed(player: PlayerEntity): Double {
        return player.engine?.speed?.toDouble() ?: 0.1
    }

    fun getJumpStrength(player: PlayerEntity): Double {
        return player.engine?.jumpStrength?.toDouble() ?: 0.1
    }

    fun onServerPlayerEntityInitialized(entity: ServerPlayerEntity) {
        val table = table.server
        val player = table.getPlayer(entity)
        if (player != null) {
            table.removePlayer(entity)
            table.setPlayer(entity, player)
        }
    }

    fun onBlockAdded(world: World, blockPos: BlockPos, state: BlockState) {
        if (world.isClient) return
        server.onPlayerBlockInteraction(blockPos, state, world)
    }

    fun onBlockRemoved(world: World, pos: BlockPos) {
        val chunk = world.getWorldChunk(pos)
        chunk.removeBlockDecals(pos)
        blockRemovedCallback?.invoke(chunk, pos)

        if (!world.isClient) {
            server.onBlockBreak(pos, world)
        }
    }

    fun shouldCancelSendJoinMessage() = chatSettings.joinMessage != "" || !chatSettings.joinMessageEnabled

    fun shouldCancelSendLeaveMessage() = chat.settings.leaveMessage != "" || !chatSettings.leaveMessageEnabled

    fun shouldCancelDamage() = !isDamageEnabled

    private val PlayerEntity.engine: EnginePlayer?
        get() = table.getGeneralPlayer(this)
}