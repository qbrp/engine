package org.lain.engine.mc

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.lain.cyberia.ecs.iterate
import org.lain.engine.item.EngineItem
import org.lain.engine.item.Item
import org.lain.engine.item.ItemId
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.GiveItemEvent
import org.lain.engine.player.get
import org.lain.engine.storage.ItemLoadContext
import org.lain.engine.world.World
import org.slf4j.LoggerFactory
import org.lain.engine.player.PlayerComponent as PlayerComponent

class MinecraftSystem(private val minecraftServer: EngineMinecraftServer) {
    private val itemStacksToLoad = mutableListOf<NotLoadedEngineItemStack>()

    fun tick(world: World) {
        world.applyInventorySignalComponents() // перед подготовкой инвентаря применяем запросы

        tickCommon(
            world,
            tickInventorySyncSystem = { tickMinecraftPlayerInventorySyncSystem() },
            tickItemStackDuplicateSystem = { tickItemStackDuplicateSystem() }
        )
        world.applyServerMinecraftPlayerGameMode()

        loadItemStacksBatch()
    }

    private fun World.applyInventorySignalComponents() {
        iterate<GiveItemEvent>() { _, (player, item, slot) ->
            val minecraftPlayer = player.get<MinecraftPlayer>() ?: return@iterate
            val minecraftEntity = minecraftPlayer.entity
            val entityInventory = minecraftEntity.inventory
            val itemStack = wrapEngineItemStack(item, ITEM_STACK_MATERIAL.copy())
            if (slot != null) {
                entityInventory.add(slot, itemStack)
            } else {
                entityInventory.add(itemStack)
            }
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
        return server.wrapItemStackCatching(ItemId(itemId), itemStack)
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
        fun tickCommon(
            world: World,
            tickInventorySyncSystem: World.() -> Unit,
            tickItemStackDuplicateSystem: World.() -> Unit = {}
        ) = with(world) {
            // перед освновным тиком очищаем состояние обновленных предметов
            world.resetMinecraftItemState()
            // тикакем системы игрока
            world.tickInventorySyncSystem()
            world.tickMinecraftPlayerInventorySystem()
            world.tickMinecraftPlayerSyncSystem()
            world.tickItemStackDuplicateSystem()
            world.synchronizeItemStacks() // назначив компоненты предметам, обновляем их

            world.synchronizeMinecraftPlayerGameMode()
        }

        val LOGGER = LoggerFactory.getLogger("Engine Minecraft Adapter")
    }
}
