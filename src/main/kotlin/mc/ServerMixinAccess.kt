package org.lain.engine.mc

import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.chunk.WorldChunk
import org.lain.cyberia.ecs.getOrSet
import org.lain.cyberia.ecs.hasComponent
import org.lain.engine.item.EngineItem
import org.lain.engine.item.gunAmmoConsumeCount
import org.lain.engine.item.merge
import org.lain.engine.player.*
import org.lain.engine.script.BuiltinScriptComponents
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.injectMinecraftEngineServer
import org.lain.engine.util.injectMovementSettings
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
    var blockPlacedCallback: ((PlayerEntity?, BlockPos, BlockState, World) -> Unit)? = null

    fun inEnginePlayer(player: ServerPlayerEntity) = table.server.getPlayer(player) != null

    fun onBlockInteraction(entity: PlayerEntity, world: World, blockPos: BlockPos): Boolean = with(server.engine.getWorld(world.engine)) {
        val voxel = chunkStorage.getDynamicVoxel(blockPos.engine()) ?: return false
        voxel.hasComponent(BuiltinScriptComponents.USE_RESTRICTION.ecsType)
    }

    fun onSlotEngineItemClicked(
        cursorItem: EngineItem,
        item: EngineItem,
        slotStack: ItemStack,
        cursorStack: ItemStack,
        player: PlayerEntity,
        clickType: ClickType
    ): Boolean {
        val space = slotStack.maxCount - slotStack.count
        if (merge(item, cursorItem) && space > 0) {
            when (clickType) {
                ClickType.LEFT -> {
                    val toMove = minOf(cursorStack.count, space)
                    slotStack.increment(toMove)
                    cursorStack.decrement(toMove)
                }
                ClickType.RIGHT -> {
                    slotStack.increment(1)
                    cursorStack.decrement(1)
                }
            }
            return true
        }

        var success = false
        val gunAmmoConsumeCount = item.gunAmmoConsumeCount(cursorItem)
        if (gunAmmoConsumeCount != 0)  {
            success = true
        }

        val enginePlayer = table.getGeneralPlayer(player) ?: return success
        enginePlayer.input.add(
            InputAction.SlotClick(cursorItem, item)
        )

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
        onPlayerEntityInstantiated(entity, table.server)
    }

    fun <P : PlayerEntity> onPlayerEntityInstantiated(entity: P, table: EntityTable.Entity2PlayerTable<P>) {
        val oldEntity = table.getEntity(entity.engineId) as? P
        if (oldEntity != null && oldEntity !== entity) {
            val player = table.getPlayer(oldEntity) ?: error("Игрок не существует")
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

    fun onBlockAdded(context: ItemPlacementContext, world: World, blockPos: BlockPos, state: BlockState) {
        val playerEntity = context.player
        if (!world.isClient) server.onBlockAdd(playerEntity?.engine, blockPos, state, world)
        blockPlacedCallback?.invoke(playerEntity, blockPos, state, world)
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