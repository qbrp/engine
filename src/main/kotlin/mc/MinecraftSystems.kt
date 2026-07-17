package org.lain.engine.mc

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import org.lain.cyberia.ecs.*
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.storage.ItemLoadContext
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.util.addIfNotNull
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.pos
import org.slf4j.LoggerFactory

context(world: World)
fun excludeEngineItemDuplicates(engineServer: EngineMinecraftServer, entity: ServerPlayer) {
    val items = mutableListOf<EngineItem>()
    for (stack in entity.visibleInventoryItems) {
        val engineItem = stack.engineItem(world) ?: continue
        if (items.contains(engineItem)) {
            engineServer.wrapItemStackCatching(engineItem.requireComponent<Item>().id, stack)
        } else {
            items.add(engineItem)
        }
    }
}

private val LOGGER = LoggerFactory.getLogger("Engine Minecraft Adapter")

context(world: World)
private fun EngineMinecraftServer.wrapItemStackCatching(itemId: ItemId, stack: ItemStack): EngineItem? {
    return try {
        wrapItemStack(itemId, stack)
    } catch (t: Throwable) {
        detachEngineItemStack(stack)
        LOGGER.error("Не удалось создать engine-предмет $itemId", t)
        null
    }
}

fun checkIsAliveDuplicate(player: EnginePlayer, entity: Player, server: MinecraftServer): Boolean {
    return !entity.isAlive && server.players.any { it.isAlive && it.engineId == player.id }
}

fun updateServerPlayerMinecraftSystems(
    server: EngineMinecraftServer,
    entityTable: EntityTable,
    world: World,
    connectionManager: ServerConnectionManager?,
    itemStacksToLoad: MutableList<NotLoadedEngineItemStack> // ВАЖНО: здесь храним копии стаков, иначе будут проблемы с потоками
) {
    val table = entityTable.server
    val engine = server.engine
    val itemLoader = engine.itemLoader
    val notUpdatedPlayers = world.players.toMutableList()

    with(world) {
        world.players.forEach { player ->
            val entity = table.getEntity(player)
            if (entity == null || checkIsAliveDuplicate(player, entity, server.minecraftServer)) {
                val reason =
                    "Какого хрена? Этой ошибки вообще не должно быть, но так уж и быть, звезды сошлись и она тебе попалась. Что делать? А?" +
                            "Спрашиваешь, что тебе делать? Может, администратору сообщить? Ну, это ты знаешь. Мой совет - молись. МОЛИСЬ, чтобы всё работало."
                connectionManager?.disconnect(connectionManager.getSession(player.id), reason) ?: error(reason)
                return@forEach
            }
            notUpdatedPlayers.remove(player)

            val entityInventory = entity.inventory
            val giveItemSignal = player.entity.removeComponent<GiveItemSignal>()
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

            val screenHandler = entity.containerMenu
            val itemStacks = entity.visibleInventoryItems + screenHandler.carried
            val items: MutableList<EngineItemStack> = mutableListOf()

            val destroyItemSignal = player.entity.removeComponent<DestroyItemSignal>()
            val loadContext = ItemLoadContext.FromInventory(
                player.id,
                player.entity.pos()
            )
            for (itemStack in itemStacks) {
                var item: EngineItem? = null
                val reference = itemStack.engine()
                if (reference != null) {
                    if (reference.version != CURRENT_ITEM_VERSION) {
                        server.wrapItemStackCatching(reference.id, itemStack)
                        continue
                    }

                    val referencedItem = reference.getItem(world)
                    val uuid = reference.uuid
                    val isItemLoaded = referencedItem != null
                    val isItemLoading = reference.loading
                    if (!isItemLoaded && !isItemLoading) {
                        reference.loading = true
                        itemStacksToLoad.add(
                            NotLoadedEngineItemStack(
                                world,
                                uuid,
                                itemStack,
                                reference,
                                loadContext
                            )
                        )
                    } else {
                        // Логика уничтожения применима только для уже загруженных предметов, очевидно
                        if (destroyItemSignal != null && referencedItem == destroyItemSignal.item) {
                            itemStack.decrement(destroyItemSignal.count)
                        }
                        if (!itemStack.isEmpty) {
                            item = referencedItem
                        }
                    }
                }

                val instantiate = itemStack.remove(ENGINE_ITEM_INSTANTIATE_COMPONENT)
                if (reference == null && instantiate != null) {
                    item = server.wrapItemStackCatching(ItemId(instantiate), itemStack)
                }

                if (item != null) items.add(EngineItemStack(item, itemStack))
            }

            updatePlayerMinecraftSystems(player.entity, items.toSet(), entity)
            excludeEngineItemDuplicates(server, entity)
            player.entity.removeComponent<WritableOpen>()
        }
    }

    if (notUpdatedPlayers.isNotEmpty()) {
        LOGGER.warn("Состояние Minecraft не обновлено для игроков: {}", notUpdatedPlayers.joinToString())
    }
}

context(world: World)
fun updatePlayerMinecraftSystems(
    player: EntityId,
    items: Set<EngineItemStack>,
    entity: Player
) {
    val location = player.requireComponent<Location>()
    val velocity = player.requireComponent<Velocity>()
    val pos = entity.position()

    velocity.prev.set(location.position)
    location.position.set(pos)

    velocity.motion.set(
        location.position.x - velocity.prev.x,
        location.position.y - velocity.prev.y,
        location.position.z - velocity.prev.z
    )

    val setVelocity = velocity.set
    if (setVelocity != null) {
        entity.deltaMovement = setVelocity.toMinecraft()
        velocity.set = null
    }

    player.requireComponent<OrientationTranslation>().apply {
        if (yaw != 0f) {
            entity.yaw += yaw
            yaw = 0f
        }
        if (pitch != 0f) {
            entity.pitch += pitch
            pitch = 0f
        }
    }

    player.requireComponent<Orientation>().apply {
        yaw = entity.yaw
        pitch = entity.pitch
    }

    player.requireComponent<EnginePlayerModel>().apply {
        standingEyeHeight = entity.eyeHeight
        height = entity.bodyHeight * scale
    }

    player.requireComponent<MovementStatus>().apply {
        isSprinting = entity.isSprinting
    }

    val spectatingComponent = player.requireComponent<Spectating>()
    val hasSpawnMark = player.hasComponent<SpawnMark>()
    val hasSpectatorMark = player.hasComponent<StartSpectatingMark>()
    if (entity is ServerPlayer) {
        val gameMode = entity.currentGameMode
        val previousGameMode = entity.previousGameMode

        if (hasSpectatorMark && gameMode != GameType.SPECTATOR) {
            entity.setGameMode(McGameModes.SPECTATOR)
        }

        if (hasSpawnMark) {
            entity.setGameMode(
                previousGameMode ?: when (entity.hasPermission("spawn.creative")) {
                    false -> McGameModes.SURVIVAL
                    true -> McGameModes.CREATIVE
                }
            )
        }

        if (!hasSpawnMark && !entity.isSpectator && spectatingComponent.enabled) {
            entity.setGameMode(McGameModes.SPECTATOR)
        }

        if (hasSpawnMark) player.removeComponent<SpawnMark>()
        if (hasSpectatorMark) player.removeComponent<StartSpectatingMark>()
    }

    player.requireComponent<GameMaster>().enabled = entity.isCreative
    spectatingComponent.enabled = entity.isSpectator

    val items = items.toMutableList()
    val playerMinecraftInventory = entity.inventory

    val playerInventory = player.requireComponent<PlayerInventory>()
    val remainingPlayerInventoryItems = playerInventory.items.toMutableList() // к удалению

    val mainItemStack = entity.mainHandItem
    val offItemStack = entity.offhandItem

    var mainHandItem: EngineItem? = null
    var offHandItem: EngineItem? = null

    for ((item, itemStack) in items) {
        if (mainItemStack?.engineItem(world) == item) mainHandItem = item
        if (offItemStack?.engineItem(world) == item) offHandItem = item

        playerInventory.items += item
        remainingPlayerInventoryItems -= item

        val updateMeta = item.getUpdateMeta()
        if (updateMeta.adaptedThisTick) {
            continue
        }
        updateMeta.adaptedThisTick = true
        updateEngineItemStack(itemStack, item)

        val countComponent = item.getComponent<Count>()
        if (countComponent == null) {
            itemStack.count = 1
        } else {
            countComponent.value = itemStack.count
        }
    }

    playerInventory.mainHandFree = mainItemStack.isEmpty
    playerInventory.mainHandItem = mainHandItem
    playerInventory.offHandItem = offHandItem
    playerInventory.selectedSlot = playerMinecraftInventory.selectedSlot

    for (removedItem in remainingPlayerInventoryItems) {
        if (removedItem != playerInventory.cursorItem) {
            playerInventory.items.remove(removedItem)
        }
    }

    player.removeComponent<GiveItemSignal>()
}

fun World.prepareItemMinecraftSystem() = iterate<Item, UpdateMeta> { item, _, updateMeta ->
    item.removeComponent<HoldsBy>()
    item.removeComponent<Location>()
    updateMeta.adaptedThisTick = false
}

fun updatePlayerOwnedItems(world: World, player: EnginePlayer) = with(world) {
    val equipmentItems = player.collectOwnedItems(world).toMutableList()
    equipmentItems.addIfNotNull(player.cursorItem)
    val location = player.entity.requireComponent<Location>() //???
    val allItems = player.items + equipmentItems
    if (allItems.isNotEmpty()) {
        val component = HoldsBy(player)
        allItems.forEach {
            it.setComponent(component)
            it.setComponent(location)
        }
    }
}