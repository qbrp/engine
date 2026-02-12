package org.lain.engine.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.minecraft.item.ItemStack
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.item.EngineItem
import org.lain.engine.item.HoldsBy
import org.lain.engine.item.ItemStorage
import org.lain.engine.item.ItemUuid
import org.lain.engine.mc.engine
import org.lain.engine.player.EnginePlayer
import org.lain.engine.server.EngineServer
import org.lain.engine.util.has
import org.lain.engine.world.location
import org.slf4j.LoggerFactory

val ItemIoCoroutineScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

class CoroutineQueueTimer<T : Any>(
    private val ticksPeriod: Long,
    scope: CoroutineScope,
    private val getter: () -> T,
    private val statement: suspend (T) -> Unit
) {
    private var tick = 0
    private val channel = Channel<T>(capacity = 1)

    init {
        scope.launch {
            for (task in channel) {
                statement(task)
            }
        }
    }

    fun activate() {
        channel.trySend(getter())
    }

    fun tick() {
        if (++tick >= ticksPeriod) {
            tick = 0
            activate()
        }
    }
}

private val LOGGER = LoggerFactory.getLogger("Engine Autosave")

fun ItemAutosaveTimer(period: Long, itemStorage: ItemStorage, database: Database) = CoroutineQueueTimer(period, ItemIoCoroutineScope, { itemStorage.getAll() }) { items ->
    database.saveItems(items)
}

suspend fun Database.saveItems(items: List<EngineItem>) {
    saveItemPersistentDataBatch(items.map { it to itemPersistentData(it) })
}

fun UnloadInactiveItemsTimer(period: Long, itemStorage: ItemStorage, database: Database, server: EngineServer) = CoroutineQueueTimer(
    period,
    ItemIoCoroutineScope,
    {
        itemStorage
            .getAll()
            .filter { !it.has<HoldsBy>() }
    }
) { items ->
    database.saveItems(items)
    server.execute {
        if (items.isNotEmpty()) {
            items.forEach { itemStorage.remove(it.uuid) }
            LOGGER.info("Выгружено и сохранено {} неактивных предметов", items.count())
        }
    }
}

suspend fun EngineMinecraftServer.loadItemStack(itemStack: ItemStack, owner: EnginePlayer) {
    val reference = itemStack.engine() ?: return
    val uuid = reference.uuid
    val item = database.loadItem(owner.location, uuid) ?: return
    minecraftServer.execute {
        wrapItemStack(owner, item, itemStack)
        engine.itemStorage.add(uuid, item)
    }
}

class ItemLoader(private val engine: EngineMinecraftServer, ) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val items = mutableMapOf<ItemUuid, Long>()
    private val notFound = mutableSetOf<ItemUuid>()

    fun isLoading(item: ItemUuid) = item in items

    fun isNotFound(item: ItemUuid) = item in notFound

    fun loadItemStack(itemStack: ItemStack, owner: EnginePlayer) {
        val uuid = itemStack.engine()?.uuid ?: return
        val time = items[uuid]
        val currentTimeMillis = System.currentTimeMillis()
        if (time == null || currentTimeMillis - time > TIMEOUT) {
            items[uuid] = currentTimeMillis
            coroutineScope.launch { engine.loadItemStack(itemStack, owner) }
        }
    }

    companion object {
        private const val TIMEOUT = 10 * 1000
    }
}