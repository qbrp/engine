package org.lain.engine.mc

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Unit
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.lain.cyberia.ecs.*
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.server.EngineServer
import org.lain.engine.storage.PersistentId
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.util.Storage
import org.lain.engine.util.math.EVec3
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.lain.engine.world.location
import org.slf4j.LoggerFactory

fun EVec3.toMinecraft(): Vec3 = Vec3(x.toDouble(), y.toDouble(), z.toDouble())

fun Vec3.engine(): EVec3 = Vec3(x.toFloat(), y.toFloat(), z.toFloat())

val Level.engine
    get() = WorldId(this.dimensionTypeRegistration().registeredName)


fun EngineServer.getWorld(world: Level): World {
    return getWorld(world.engine)
}

fun Username(text: Component) = Username(text.string)

context(world: World)
fun excludeEngineItemDuplicates(engineServer: EngineMinecraftServer, entity: ServerPlayer, player: EnginePlayer) {
    val items = mutableListOf<EngineItem>()
    for (stack in entity.inventory.iterator()) {
        val engineItem = stack.engineItem() ?: continue
        if (items.contains(engineItem)) {
            engineServer.wrapItemStackCatching(player, engineItem.requireComponent<ItemMeta>().id, stack)
        } else {
            items.add(engineItem)
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

fun checkIsAliveDuplicate(player: EnginePlayer, entity: Player, server: MinecraftServer): Boolean {
    return !entity.isAlive && server.players.any { it.isAlive && it.engineId == player.id }
}

private val ItemStackIoCoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

data class EngineItemStack(val engineItem: EngineItem, val itemStack: ItemStack)

data class NotLoadedEngineItemStack(val itemUuid: PersistentId, val itemStack: ItemStack)

fun updateServerMinecraftSystems(
    server: EngineMinecraftServer,
    table: ServerPlayerTable,
    players: List<EnginePlayer>,
    connectionManager: ServerConnectionManager?,
) {
    val engine = server.engine
    val itemStorage = engine.itemStorage
    val itemLoader = engine.itemLoader
    val notUpdatedPlayers = players.toMutableList()

    server.engine.allWorlds().forEach { world -> world.prepareItemMinecraftSystem() }
    for (player in players) {
        val entity = table.getEntity(player)
        if (entity == null || checkIsAliveDuplicate(player, entity, server.minecraftServer)) {
            val reason = "Какого хрена? Этой ошибки вообще не должно быть, но так уж и быть, звезды сошлись и она тебе попалась. Что делать? А?" +
                    "Спрашиваешь, что тебе делать? Может, администратору сообщить? Ну, это ты знаешь. Мой совет - молись. МОЛИСЬ, чтобы всё работало."
            connectionManager?.disconnect(connectionManager.getSession(player.id), reason) ?: error(reason)
            continue
        }

        val world = engine.getWorld(entity.level())
        notUpdatedPlayers.remove(player)

        val screenHandler = entity.containerMenu
        val itemStacks = entity.inventory + screenHandler.items + screenHandler.carried
        val items: MutableList<EngineItemStack> = mutableListOf()
        val itemStacksToLoad = mutableListOf<NotLoadedEngineItemStack>() // ВАЖНО: здесь храним копии стаков, иначе будут проблемы с потоками

        for (itemStack in itemStacks) {
            var item: EngineItem? = null
            val reference = itemStack.engine()
            if (reference != null) {
                val referencedItem = reference.getItem()
                val uuid = reference.uuid
                val isItemLoaded = referencedItem != null
                val isItemLoading = itemStack.has(ENGINE_ITEM_LOADING_COMPONENT)
                if (!isItemLoaded && !isItemLoading) {
                    itemStack.set(ENGINE_ITEM_LOADING_COMPONENT, Unit.INSTANCE)
                    itemStacksToLoad.add(NotLoadedEngineItemStack(uuid, itemStack.copy()))
                } else {
                    item = referencedItem
                }
            }

            val instantiate = itemStack.remove(ENGINE_ITEM_INSTANTIATE_COMPONENT)
            if (reference == null && instantiate != null) {
                item = server.wrapItemStackCatching(player, ItemId(instantiate), itemStack)
            }

            if (item != null) items.add(EngineItemStack(item, itemStack))
        }


        if (itemStacksToLoad.isNotEmpty()) {
            ItemStackIoCoroutineScope.launch {
                val semaphore = Semaphore(16)

                itemStacksToLoad
                    .map { data ->
                        async {
                            semaphore.withPermit {
                                try {
                                    withTimeout(5000) {
                                        val item = itemLoader.loadWorldItem(data.itemUuid, world)
                                        server.engine.execute {
                                            context(world) {
                                                wrapEngineItemStack(item, data.itemStack)
                                            }
                                        }
                                    }
                                } finally {
                                    server.engine.execute {
                                        data.itemStack.remove(ENGINE_ITEM_LOADING_COMPONENT)
                                    }
                                }
                            }
                        }
                    }
                    .awaitAll()
            }
        }

        updatePlayerMinecraftSystems(player, items.toSet(), entity, world, engine.itemStorage)
        player.remove<OpenBookTag>()
        if (entity is ServerPlayer) {
            with(world) { excludeEngineItemDuplicates(server, entity, player) }
        }
    }
    if (notUpdatedPlayers.isNotEmpty()) {
        LOGGER.warn("Состояние Minecraft не обновлено для игроков: {}", notUpdatedPlayers.joinToString())
    }
}

fun updatePlayerMinecraftSystems(
    player: EnginePlayer,
    items: Set<EngineItemStack>,
    entity: Player,
    world: World,
    itemStorage: Storage<String, EngineItem>
) = with(world) {
    val items = items.toMutableList()

    val location = player.require<Location>()
    val velocity = player.require<Velocity>()
    val pos = entity.position()

    velocity.prev.set(location.position)
    location.position.set(pos)
    location.world = world

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
        standingEyeHeight = entity.eyeHeight
        height = entity.bodyHeight * scale
    }

    player.apply<MovementStatus> {
        isSprinting = entity.isSprinting
    }

    if (entity is ServerPlayer) {
        val hasSpawnMark = player.has<SpawnMark>()
        val hasSpectatorMark = player.has<StartSpectatingMark>()
        val gameMode = entity.currentGameMode
        val previousGameMode = entity.previousGameMode

        if (hasSpectatorMark && gameMode != GameType.SPECTATOR) {
            entity.setGameMode(McGameModes.SURVIVAL)
        }

        if (hasSpawnMark) {
            entity.setGameMode(previousGameMode ?: when(entity.hasPermission("spawn.creative")) {
                false -> McGameModes.CREATIVE
                true -> McGameModes.SURVIVAL
            })
        }

        if (hasSpawnMark) player.remove<SpawnMark>()
        if (hasSpectatorMark) player.remove<StartSpectatingMark>()
    }

    player.isInGameMasterMode = entity.isCreative
    player.isSpectating = entity.isSpectator

    val playerMinecraftInventory = entity.inventory
    val playerInventory = player.require<PlayerInventory>()
    val playerInventoryItems = playerInventory.items.toMutableList()
    val destroyItemSignal = player.get<DestroyItemSignal>()
    val moveItemSignal = player.get<MoveItemSignal>()

    fun insertStack(item: EngineItem, itemStack: ItemStack, slot: Int?) {
        if (slot != null) {
            playerMinecraftInventory.add(slot, itemStack)
        } else {
            playerMinecraftInventory.add(itemStack)
        }
        items += EngineItemStack(item, itemStack)
    }

    if (moveItemSignal != null && entity is ServerPlayer) {
        val item = moveItemSignal.item
        val itemStack = wrapEngineItemStack(item, ITEM_STACK_MATERIAL.copy())
        val slot = moveItemSignal.slot
        insertStack(item, itemStack, slot)
    }

    val mainItemStack = entity.mainHandItem
    val mainHandStackItem = mainItemStack.engine()?.uuid?.let { itemStorage.get(it.value) }
    val offHandStackItem = entity.offhandItem.engine()?.uuid?.let { itemStorage.get(it.value) }
    var mainHandItem: EngineItem? = null
    var offHandItem: EngineItem? = null

    for ((item, itemStack) in items) {
        if (mainHandStackItem == item) {
            mainHandItem = item
        }
        if (offHandStackItem == item) {
            offHandItem = item
        }

        playerInventory.items += item
        playerInventoryItems -= item

        if (destroyItemSignal != null && destroyItemSignal.item == item) {
            itemStack.decrement(destroyItemSignal.count)
            player.remove<DestroyItemSignal>()
        }

        if (!item.hasComponent<UpdateMeta>()) {
            println() //FOR DEBUG
        }

        val updateMeta = item.requireComponent<UpdateMeta>()
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

    for (removedItem in playerInventoryItems) {
        if (removedItem != playerInventory.cursorItem) {
            playerInventory.items.remove(removedItem)
        }
    }

    player.remove<MoveItemSignal>()
}

fun World.prepareItemMinecraftSystem() = iterate<Item, UpdateMeta> { item, _, updateMeta ->
    item.removeComponent<HoldsBy>()
    item.removeComponent<Location>()
    updateMeta.adaptedThisTick = false
}

fun updatePlayerOwnedItems(world: World, player: EnginePlayer) = with(world) {
    val equipmentItems = player.collectOwnedItems(world)
    val location = player.location
    val allItems = player.items + equipmentItems
    if (allItems.isNotEmpty()) {
        val component = HoldsBy(player)
        allItems.forEach {
            it.setComponent(component)
            it.setComponent(location)
        }
    }
}