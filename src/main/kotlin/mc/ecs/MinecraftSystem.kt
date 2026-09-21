package org.lain.engine.mc.ecs

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.lain.cyberia.ecs.iterate
import org.lain.engine.item.EngineItem
import org.lain.engine.item.Item
import org.lain.engine.item.ItemId
import org.lain.engine.mc.bindMinecraftEntity
import org.lain.engine.mc.engine
import org.lain.engine.mc.getPlayer
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.mc.visibleInventoryItems
import org.lain.engine.player.EnginePlayer
import org.lain.engine.script.EngineId
import org.lain.engine.data.ItemLoadContext
import org.lain.engine.world.World
import org.slf4j.LoggerFactory
import org.lain.engine.player.PlayerComponent as PlayerComponent

class MinecraftSystem(private val minecraftServer: EngineMinecraftServer) {
    private val itemStacksToLoad = mutableListOf<NotLoadedEngineItemStack>()

    fun tick(world: World) {
        refreshMinecraftPlayerEntityReferences(world)
        tickDataPrepareCommon(
            world,
            tickInventorySyncSystem = { tickMinecraftPlayerInventorySyncSystem() },
            tickItemStackDuplicateSystem = { tickItemStackDuplicateSystem() }
        )
        world.applyServerMinecraftPlayerGameMode()

        loadItemStacksBatch()
    }

    private fun refreshMinecraftPlayerEntityReferences(world: World) {
        for (player in world.players) {
            val currentEntity = minecraftServer.minecraftServer.getPlayer(player.id) ?: continue
            player.bindMinecraftEntity(currentEntity)
        }
    }

    private fun World.tickMinecraftPlayerInventorySyncSystem() {
        iterate<PlayerComponent, MinecraftPlayer> { _, (player), minecraftPlayer ->
            val minecraftEntity = minecraftPlayer.entity
            minecraftPlayer.itemStacks = (minecraftEntity.visibleInventoryItems.asSequence() +
                sequenceOf(minecraftEntity.containerMenu.carried))
                .mapNotNull { itemStack ->
                    resolveInventoryEngineItem(player, minecraftEntity, itemStack)
                        ?.let { item -> EngineItemStack(item, itemStack) }
                }
        }
    }

    private fun World.resolveInventoryEngineItem(
        player: EnginePlayer,
        minecraftEntity: Player,
        itemStack: ItemStack,
    ): EngineItem? {
        val server = this@MinecraftSystem.minecraftServer
        if (itemStack.isEmpty) return null

        val reference = itemStack.engine()
        if (reference != null) {
            if (reference.version != CURRENT_ITEM_VERSION) {
                return server.wrapItemStackCatching(reference.id, itemStack)
            }

            val item = reference.getItem(this)
            if (item != null) return item

            if (!reference.loading) {
                reference.loading = true
                itemStacksToLoad += NotLoadedEngineItemStack(
                    this,
                    reference.uuid,
                    itemStack,
                    reference,
                    ItemLoadContext.FromInventory(
                        player.id,
                        minecraftEntity.position().engine()
                    )
                )
            }
            return null
        }

        val itemId = itemStack.remove(ENGINE_ITEM_INSTANTIATE_COMPONENT) ?: return null
        return server.wrapItemStackCatching(ItemId(EngineId(itemId)), itemStack)
    }

    private fun World.tickItemStackDuplicateSystem() {
        iterate<MinecraftItem, Item> { entity, (itemStacks), item ->
            if (itemStacks.size > 1) {
                itemStacks.drop(1).forEach { itemStack ->
                    minecraftServer.wrapItemStackCatching(item.id, itemStack)
                }
            }
        }
    }

    private fun loadItemStacksBatch() {
        if (itemStacksToLoad.isEmpty()) return

        val pendingLoads = itemStacksToLoad.toList()
        itemStacksToLoad.clear()
        updateMinecraftItemLoadSystem(pendingLoads, this@MinecraftSystem.minecraftServer.engine)
    }

    companion object {
        fun tickDataPrepareCommon(
            world: World,
            tickInventorySyncSystem: World.() -> Unit,
            tickItemStackDuplicateSystem: World.() -> Unit = {}
        ) = with(world) {
            // сначала выдаем новые предметы
            tickGiveItemSystem()
            tickMinecraftEntitySystem()

            // перед освновным тиком очищаем состояние обновленных предметов
            resetMinecraftItemState()
            // тикакем системы игрока
            tickInventorySyncSystem()
            tickMinecraftPlayerInventorySystem()
            tickMinecraftPlayerSyncSystem()
            tickItemStackDuplicateSystem()
            synchronizeItemStacks() // назначив компоненты предметам, обновляем их

            synchronizeMinecraftPlayerGameMode()
        }

        val LOGGER = LoggerFactory.getLogger("Engine Minecraft Adapter")
    }

    fun tickOperationsApplyCommon(world: World) = with(world) {

    }
}
