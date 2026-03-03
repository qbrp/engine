package org.lain.engine.mc

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import org.lain.engine.CommonEngineServerMod
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.server.EngineServer
import org.lain.engine.server.desync
import org.lain.engine.storage.ItemLoader
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.util.*
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.slf4j.LoggerFactory
import net.minecraft.world.World as McWorld

fun Vec3.toMinecraft(): Vec3d = Vec3d(x.toDouble(), y.toDouble(), z.toDouble())

fun Vec3d.engine(): Vec3 = Vec3(x.toFloat(), y.toFloat(), z.toFloat())

val McWorld.engine
    get() = WorldId(this.registryKey.value.toString())


fun EngineServer.getWorld(world: McWorld): World {
    return getWorld(world.engine)
}

fun EngineServer.getPlayerWorld(player: PlayerEntity): World {
    return getWorld(player.entityWorld)
}

val ENGINE_MOD_VERSION
    get() = FabricLoader.getInstance().getModContainer(CommonEngineServerMod.MOD_ID).get().metadata.version.friendlyString

fun Username(text: Text) = Username(text.string)

fun excludeEngineItemDuplicates(engineServer: EngineMinecraftServer, entity: ServerPlayerEntity, player: EnginePlayer) {
    val items = mutableListOf<ItemUuid>()
    for (stack in entity.inventory.mainStacks) {
        val engineItem = stack.engine() ?: continue
        val itemUuid = engineItem.uuid
        if (items.contains(itemUuid)) {
            engineServer.wrapItemStackCatching(player, engineItem.id, stack)
        } else {
            items.add(itemUuid)
        }
    }
}

fun updateLegacyEngineItems(server: EngineMinecraftServer, player: EnginePlayer, itemStacks: List<ItemStack>) {
    for (item in itemStacks) {
        if (item.contains(ENGINE_ITEM_REFERENCE_COMPONENT_LEGACY)) {
            val component = item.remove(ENGINE_ITEM_REFERENCE_COMPONENT_LEGACY)!!
            server.wrapItemStackCatching(player, component.item, item)
        }
    }
}

private val LOGGER = LoggerFactory.getLogger("Engine Minecraft Adapter")

private fun EngineMinecraftServer.wrapItemStackCatching(player: EnginePlayer, itemId: ItemId, stack: ItemStack): EngineItem? {
    return try {
        wrapItemStack(player, itemId, stack)
    } catch (t: Throwable) {
        detachEngineItemStack(stack)
        LOGGER.error("Не удалось создать engine-предмет $itemId", t)
        null
    }
}

fun checkIsAliveDuplicate(player: EnginePlayer, entity: PlayerEntity, server: MinecraftServer): Boolean {
    return !entity.isAlive && server.playerManager.playerList.any { it.isAlive && it.engineId == player.id }
}

fun updateServerMinecraftSystems(
    server: EngineMinecraftServer,
    table: ServerPlayerTable,
    players: List<EnginePlayer>,
    itemLoader: ItemLoader,
    connectionManager: ServerConnectionManager?
) {
    val engine = server.engine
    val notUpdatedPlayers = players.toMutableList()

    for (player in players) {
        val entity = table.getEntity(player)
        if (entity == null || checkIsAliveDuplicate(player, entity, server.minecraftServer)) {
            val reason = "Какого хрена? Этой ошибки вообще не должно быть, но так уж и быть, звезды сошлись и она тебе попалась. Что делать? А?" +
                    "Спрашиваешь, что тебе делать? Может, администратору сообщить? Ну, это ты знаешь. Мой совет - молись, молись, чтобы всё работало."
            connectionManager?.disconnect(connectionManager.getSession(player.id), reason) ?: error(reason)
            continue
        }

        val world = engine.getWorld(entity.entityWorld)
        notUpdatedPlayers.remove(player)

        val screenHandler = entity.currentScreenHandler
        val itemStacks = entity.inventory + screenHandler.stacks + screenHandler.cursorStack
        updateLegacyEngineItems(server, player, itemStacks)
        val items: MutableList<Pair<EngineItem, ItemStack>> = mutableListOf()

        for (itemStack in itemStacks) {
            var item: EngineItem? = null
            val reference = itemStack.engine()
            if (reference != null) {
                item = reference.getItem()
                val uuid = reference.uuid
                if (item == null) {
                    if (!itemLoader.isLoading(uuid)) {
                        itemLoader.loadItemStack(itemStack, player)
                    } else if (itemLoader.isNotFound(uuid)) {
                        detachEngineItemStack(itemStack)
                    }
                }
            }

            val instantiate = itemStack.remove(ENGINE_ITEM_INSTANTIATE_COMPONENT)
            if (reference == null && instantiate != null) {
                item = server.wrapItemStackCatching(player, ItemId(instantiate), itemStack)
            }

            if (item != null) {
                items.add(item to itemStack)
            }
        }
        updatePlayerMinecraftSystems(player, items.toSet(), entity, world, engine.itemStorage)
        player.remove<OpenBookTag>()
        if (entity is ServerPlayerEntity) {
            excludeEngineItemDuplicates(server, entity, player)
        }
    }
    if (notUpdatedPlayers.isNotEmpty()) {
        LOGGER.warn("Состояние Minecraft не обновлено для игроков: {}", notUpdatedPlayers.joinToString())
    }
}

fun updatePlayerMinecraftSystems(
    player: EnginePlayer,
    items: Set<Pair<EngineItem, ItemStack>>,
    entity: PlayerEntity,
    world: World,
    itemStorage: Storage<ItemUuid, EngineItem>
) {
    val location = player.require<Location>()
    val velocity = player.require<Velocity>()
    val pos = entity.entityPos

    velocity.prev.set(location.position)
    location.position.set(pos)
    location.world = world

    velocity.motion.set(
        location.position.x - velocity.prev.x,
        location.position.y - velocity.prev.y,
        location.position.z - velocity.prev.z
    )

    player.apply<OrientationTranslation> {
        if (yaw != 0f) {
            entity.yaw += yaw
            yaw = 0f
        }
        if (pitch != 0f) {
            entity.pitch += pitch
            pitch = 0f
        }
    }

    player.apply<Orientation> {
        yaw = entity.yaw
        pitch = entity.pitch
    }

    player.apply<PlayerModel> {
        scale = entity.scale
        standingEyeHeight = entity.standingEyeHeight
        height = entity.height * scale
    }

    player.apply<MovementStatus> {
        isSprinting = entity.isSprinting
    }

    if (entity is ServerPlayerEntity) {
        val hasSpawnMark = player.has<SpawnMark>()
        val hasSpectatorMark = player.has<StartSpectatingMark>()
        val interactionManager = entity.interactionManager
        val gameMode = interactionManager.gameMode
        val previousGameMode = interactionManager.previousGameMode

        if (hasSpectatorMark && gameMode != GameMode.SPECTATOR) {
            entity.changeGameMode(GameMode.SPECTATOR)
        }

        if (hasSpawnMark) {
            entity.changeGameMode(previousGameMode ?: entity.resolveGameMode())
        }

        if (hasSpawnMark) player.remove<SpawnMark>()
        if (hasSpectatorMark) player.remove<StartSpectatingMark>()
    }

    player.isInGameMasterMode = entity.isCreative
    player.isSpectating = entity.isSpectator

    val playerInventory = player.require<PlayerInventory>()
    val playerInventoryItems = playerInventory.items.toMutableList()
    val destroyItemSignal = player.get<DestroyItemSignal>()
    val moveItemSignal = player.get<MoveItemSignal>()

    val mainItemStack = entity.mainHandStack
    val mainHandStackId = mainItemStack.engine()?.uuid
    val offHandStackId = entity.offHandStack.engine()?.uuid
    var mainHandItem: EngineItem? = null
    var offHandItem: EngineItem? = null

    if (moveItemSignal != null) {
        val item = itemStorage.get(moveItemSignal.item) ?: desync("Предмет для передачи ${moveItemSignal.item} не найден")
        entity.inventory.insertStack(
            moveItemSignal.slot,
            wrapEngineItemStack(item, ITEM_STACK_MATERIAL.copy())
        )
    }

    for ((item, itemStack) in items) {
        if (mainHandStackId == item.uuid) {
            mainHandItem = item
        }
        if (offHandStackId == item.uuid) {
            offHandItem = item
        }

        item.getOrSet { HoldsBy(player.id) }
        playerInventory.items += item
        playerInventoryItems -= item

        updateEngineItemStack(itemStack, item)

        if (destroyItemSignal != null && destroyItemSignal.item == item.uuid) {
            itemStack.decrement(destroyItemSignal.count)
        }

        val countComponent = item.get<Count>()
        if (countComponent == null) {
            itemStack.count = 1
        } else {
            countComponent.value = itemStack.count
        }
    }

    playerInventory.mainHandItem = mainHandItem
    playerInventory.offHandItem = offHandItem
    playerInventory.selectedSlot = entity.inventory.selectedSlot

    for (removedItem in playerInventoryItems) {
        if (removedItem != playerInventory.cursorItem) {
            playerInventory.items.remove(removedItem)
            removedItem.remove<HoldsBy>()
        }
    }

    player.remove<DestroyItemSignal>()
    player.remove<MoveItemSignal>()
}

private fun ServerPlayerEntity.resolveGameMode() = if (this.hasPermissionLevel(4)) {
    GameMode.CREATIVE
} else {
    GameMode.SURVIVAL
}