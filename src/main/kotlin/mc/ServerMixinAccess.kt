package org.lain.engine.mc

import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.chunk.WorldChunk
import org.lain.engine.item.EngineItem
import org.lain.engine.item.gunAmmoConsumeCount
import org.lain.engine.item.merge
import org.lain.engine.player.*
import org.lain.engine.util.*
import org.lain.engine.util.text.displayNameMiniMessage
import org.lain.engine.util.text.parseMiniMessageLegacy
import org.lain.engine.world.world

object ServerMixinAccess {
    private val table by injectEntityTable()
    private val server by injectMinecraftEngineServer()
    private val chat get() = server.engine.chat
    private val chatSettings get() = chat.settings
    var disableAchievementMessages = false
    var isDamageEnabled = false
    var blockRemovedCallback: ((WorldChunk, BlockPos) -> Unit)? = null

    fun inEnginePlayer(player: ServerPlayerEntity) = table.server.getPlayer(player) != null

    fun onSlotEngineItemClicked(cursorItem: EngineItem, item: EngineItem, slotStack: ItemStack, cursorStack: ItemStack, player: PlayerEntity): Boolean {
        if (merge(item, cursorItem)) {
            val space = slotStack.maxCount - slotStack.count
            return if (space > 0) {
                val toMove = minOf(cursorStack.count, space)
                slotStack.increment(toMove)
                cursorStack.decrement(toMove)
                true
            } else {
                false
            }
        }

        var success = false
        val gunAmmoConsumeCount = item.gunAmmoConsumeCount(cursorItem)
        if (gunAmmoConsumeCount != 0)  {
            success = true
        }

        val enginePlayer = table.getGeneralPlayer(player) ?: return success
        enginePlayer.set(InteractionComponent(Interaction.SlotClick(cursorItem, item)))

        return success
    }

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

    fun onPlayerJump(entity: PlayerEntity) {
        entity.engine?.getOrSet { Jump }
    }

    fun canJump(entity: PlayerEntity): Boolean {
        return entity.engine?.let { player ->
            val settings by injectMovementSettings()
            canPlayerJump(player, settings)
        } ?: true
    }

    fun onChunkDataSent(chunk: WorldChunk, player: ServerPlayerEntity) {
        val player = table.server.getPlayer(player) ?: return
        val chunkStorage = player.world.chunkStorage
        val chunkPos = chunk.pos.engine()
        val engineChunk = chunkStorage.requireChunk(chunkPos)
        server.engine.handler.onChunkSend(engineChunk, chunkPos, player)
    }

    fun onBlockAdded(world: World, blockPos: BlockPos, state: BlockState) {
        if (world.isClient) return
        server.onPlayerBlockInteraction(blockPos, state, world)
    }

    fun onBlockRemoved(world: World, pos: BlockPos) {
        val chunk = world.getWorldChunk(pos)
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