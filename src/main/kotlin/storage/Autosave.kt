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

fun ItemAutosaveTimer(period: Long, itemStorage: ItemStorage, database: Database) = CoroutineQueueTimer(
    period,
    ItemIoCoroutineScope,
    { itemStorage.getAll().mapPersistentData() }
) { items ->
    database.saveItemPersistentDataBatch(items)
}

fun List<EngineItem>.mapPersistentData() = map { it to itemPersistentData(it) }

fun UnloadInactiveItemsTimer(period: Long, itemStorage: ItemStorage, database: Database, server: EngineServer) = CoroutineQueueTimer(
    period,
    ItemIoCoroutineScope,
    {
        itemStorage
            .getAll()
            .filter { !it.has<HoldsBy>() }
            .mapPersistentData()
    }
) { items ->
    val engineItems = items.map { it.first.uuid }
    database.saveItemPersistentDataBatch(items)
    server.execute {
        if (items.isNotEmpty()) {
            server.handler.onItemsBatchDestroy(engineItems)
            items.forEach { itemStorage.remove(it.first.uuid) }
            LOGGER.info("Выгружено и сохранено {} неактивных предметов", items.count())
        }
    }
}

suspend fun EngineMinecraftServer.loadItemStack(itemStack: ItemStack, owner: EnginePlayer): EngineItem? {
    val reference = itemStack.engine() ?: return null
    val uuid = reference.uuid
    return database.loadItem(owner.location, uuid)
        ?.also { engine.itemStorage.add(it.uuid, it) }
}

class ItemLoader(private val server: EngineMinecraftServer, ) {
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
            coroutineScope.launch {
                val item = server.loadItemStack(itemStack, owner) ?: run {
                    notFound.add(uuid)
                    return@launch
                }
                server.engine.execute { server.wrapItemStack(owner, item, itemStack) }
            }
        }
    }

    companion object {
        private const val TIMEOUT = 10 * 1000
    }
}