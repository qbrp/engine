package org.lain.engine.mc

import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.EngineItem
import org.lain.engine.item.Item
import org.lain.engine.item.ItemId
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.GiveItemSignal
import org.lain.engine.storage.ItemLoadContext
import org.lain.engine.world.World
import org.slf4j.LoggerFactory
import org.lain.engine.player.Player as PlayerComponent

class MinecraftSystem(
    private val entityTable: EntityTable,
    private val server: EngineMinecraftServer,
) {
    private val connectionManager = server.connectionManager
    private val itemStacksToLoad = mutableListOf<NotLoadedEngineItemStack>()

    fun tick(world: World) {
        world.applyMinecraftPlayerComponents() // подготавливаем данные
        world.applyInventorySignalComponents() // перед подготовкой инвентаря применяем сигналы

        // перед освновным тиком очищаем состояние обновленных предметов
        world.resetMinecraftItemState()
        // тикакем системы игрока
        world.synchronizeMinecraftPlayerState()
        world.synchronizeMinecraftPlayerInventory()
        world.tickItemStackDuplicateSystem()
        world.synchronizeItemStacks() // назначив компоненты предметам, обновляем их

        world.synchronizeMinecraftPlayerGameMode()
        world.applyServerMinecraftPlayerGameMode()

        startMinecraftItemLoadSystem()
    }

    fun World.applyMinecraftPlayerComponents() {
        iterate<PlayerComponent> { entity, (player) ->
            val minecraftEntity = entityTable.server.getEntity(player)
            if (minecraftEntity == null || checkIsAliveDuplicate(player, minecraftEntity, server.minecraftServer)) {
                val reason =
                    "Какого хрена? Этой ошибки вообще не должно быть, но так уж и быть, звезды сошлись и она тебе попалась. Что делать? А?" +
                            "Спрашиваешь, что тебе делать? Может, администратору сообщить? Ну, это ты знаешь. Мой совет - молись. МОЛИСЬ, чтобы всё работало."
                connectionManager?.disconnect(connectionManager.getSession(player.id), reason) ?: error(reason)
                return@iterate
            }

            if (!entity.hasComponent<MinecraftPlayer>()) {
                entity.setComponent(MinecraftPlayer(minecraftEntity))
            }
        }
    }

    private fun World.applyInventorySignalComponents() {
        iterate<MinecraftPlayer>() { entity, minecraftPlayer ->
            val minecraftEntity = minecraftPlayer.entity
            val entityInventory = minecraftEntity.inventory
            val giveItemSignal = entity.removeComponent<GiveItemSignal>()
            if (giveItemSignal != null) {
                val item = giveItemSignal.item
                val itemStack = wrapEngineItemStack(item, ITEM_STACK_MATERIAL.copy())
                val slot = giveItemSignal.slot
                if (slot != null) {
                    entityInventory.add(slot, itemStack)
                } else {
                    entityInventory.add(itemStack)
                }
            }
        }
    }

    private fun World.synchronizeMinecraftPlayerInventory() {
        iterate<PlayerComponent, MinecraftPlayer> { _, (player), minecraftPlayer ->
            val minecraftEntity = minecraftPlayer.entity
            val items = (minecraftEntity.visibleInventoryItems.asSequence() +
                sequenceOf(minecraftEntity.containerMenu.carried))
                .mapNotNull { itemStack ->
                    resolveInventoryEngineItem(player, minecraftEntity, itemStack)
                        ?.let { item -> EngineItemStack(item, itemStack) }
                }
            synchronizeMinecraftPlayerInventory(player.entity, items, minecraftEntity)
        }
    }

    private fun World.resolveInventoryEngineItem(
        player: EnginePlayer,
        minecraftEntity: Player,
        itemStack: ItemStack,
    ): EngineItem? {
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
                    server.wrapItemStackCatching(item.id, itemStack)
                }
            }
        }
    }

    private fun startMinecraftItemLoadSystem() {
        if (itemStacksToLoad.isEmpty()) return

        val pendingLoads = itemStacksToLoad.toList()
        itemStacksToLoad.clear()
        updateMinecraftItemLoadSystem(pendingLoads, server.engine)
    }

    private fun checkIsAliveDuplicate(player: EnginePlayer, entity: Player, server: MinecraftServer): Boolean {
        return !entity.isAlive && server.players.any { it.isAlive && it.engineId == player.id }
    }

    companion object {
        val LOGGER = LoggerFactory.getLogger("Engine Minecraft Adapter")
    }
}
