package org.lain.engine.mc

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import org.lain.cyberia.ecs.getOrSet
import org.lain.cyberia.ecs.hasComponent
import org.lain.engine.item.EngineItem
import org.lain.engine.item.gunAmmoConsumeCount
import org.lain.engine.item.merge
import org.lain.engine.player.*
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.injectMinecraftEngineServer
import org.lain.engine.util.injectMovementSettings
import org.lain.engine.world.world

object ServerMixinAccess {
    private val table by injectEntityTable()
    private val server by injectMinecraftEngineServer()
    private val chat get() = server.engine.chat
    private val chatSettings get() = chat.settings
    var disableAchievementMessages = false
    var isDamageEnabled = false
    var blockRemovedCallback: ((LevelChunk, BlockPos) -> Unit)? = null
    var blockPlacedCallback: ((Player?, BlockPos, BlockState, Level) -> Unit)? = null
    var blockInteractionCallback: ((entity: Player, world: Level, blockPos: BlockPos) -> Boolean)? = null

    fun inEnginePlayer(player: ServerPlayer) = table.server.getPlayer(player) != null

    fun onBlockInteraction(entity: Player, world: Level, blockPos: BlockPos): Boolean {
        return if (!world.isClientSide) {
            val world = server.engine.getWorld(world.engine)
            val voxel = world.chunkStorage.getDynamicVoxel(blockPos.voxelPos()) ?: return false
            with(world) { voxel.hasComponent(CoreScriptComponents.USE_RESTRICTION) }
        } else {
            blockInteractionCallback?.invoke(entity, world, blockPos) ?: false
        }
    }

    fun onSlotEngineItemClicked(
        cursorItem: EngineItem,
        item: EngineItem,
        slotStack: ItemStack,
        cursorStack: ItemStack,
        player: Player,
        clickType: ClickAction
    ): Boolean {
        val world = player.engine?.world ?: return false
        val space = slotStack.maxStackSize - slotStack.count
        if (world.merge(item, cursorItem) && space > 0) {
            when (clickType) {
                ClickAction.PRIMARY  -> {
                    val toMove = minOf(cursorStack.count, space)
                    slotStack.increment(toMove)
                    cursorStack.decrement(toMove)
                }
                ClickAction.SECONDARY -> {
                    slotStack.increment(1)
                    cursorStack.decrement(1)
                }
            }
            return true
        }

        var success = false
        val gunAmmoConsumeCount = item.gunAmmoConsumeCount(world, cursorItem)
        if (gunAmmoConsumeCount != 0)  {
            success = true
        }

        val enginePlayer = table.getGeneralPlayer(player) ?: return success
        enginePlayer.input.add(InputAction.SlotClick(cursorItem, item))

        return success
    }

    fun isAchievementMessagesDisabled() = disableAchievementMessages

    fun getDisplayName(player: Player): Component? {
        return player.engine?.displayNameMiniMessage?.parseMiniMessageLegacy() ?: player.name
    }

    fun getSpeed(player: Player): Double {
        return player.engine?.speed?.toDouble() ?: 0.1
    }

    fun getJumpStrength(player: Player): Double {
        return player.engine?.jumpStrength?.toDouble() ?: 0.1
    }

    fun onServerPlayerInitialized(entity: ServerPlayer) {
        onPlayerInstantiated(entity, table.server)
    }

    fun <P : Player> onPlayerInstantiated(entity: P, table: EntityTable.Entity2PlayerTable<P>) {
        val oldEntity = table.getEntity(entity.engineId) as? P
        if (oldEntity != null && oldEntity !== entity) {
            val player = table.getPlayer(oldEntity) ?: error("Игрок не существует")
            table.removePlayer(entity)
            table.setPlayer(entity, player)
        }
    }

    fun onPlayerJump(entity: Player) {
        entity.engine?.getOrSet { Jump }
    }

    fun canJump(entity: Player): Boolean {
        return entity.engine?.let { player ->
            val settings by injectMovementSettings()
            canPlayerJump(player, settings)
        } ?: true
    }

    fun onChunkDataSent(chunk: LevelChunk, player: ServerPlayer) {
        val player = table.server.getPlayer(player) ?: return
        val world = player.world
        val chunkStorage = world.chunkStorage
        val chunkPos = chunk.pos.engineChunkPos()
        val engineChunk = chunkStorage.requireChunk(chunkPos)
        server.engine.handler.onChunkSend(world, engineChunk, chunkPos, player)
    }

    fun onBlockAdded(context: BlockPlaceContext, world: Level, blockPos: BlockPos, state: BlockState) {
        val playerEntity = context.player
        if (!world.isClientSide) server.onBlockAdd(playerEntity?.engine, blockPos, state, world)
        blockPlacedCallback?.invoke(playerEntity, blockPos, state, world)
    }

    fun onBlockRemoved(world: Level, pos: BlockPos) {
        val chunk = world.getChunkAt(pos)
        blockRemovedCallback?.invoke(chunk, pos)

        if (!world.isClientSide) {
            server.onBlockBreak(pos, world)
        }
    }

    fun shouldCancelSendJoinMessage() = chatSettings.joinMessage != "" || !chatSettings.joinMessageEnabled

    fun shouldCancelSendLeaveMessage() = chat.settings.leaveMessage != "" || !chatSettings.leaveMessageEnabled

    fun shouldCancelDamage() = !isDamageEnabled

    private val Player.engine: EnginePlayer?
        get() = table.getGeneralPlayer(this)
}