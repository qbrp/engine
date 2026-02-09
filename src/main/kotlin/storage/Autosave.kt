package org.lain.engine.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.engine.item.HoldsBy
import org.lain.engine.item.ItemStorage
import org.lain.engine.item.ItemUuid
import org.lain.engine.server.EngineServer
import org.lain.engine.util.has
import org.lain.engine.world.Location

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

    fun tick() {
        if (++tick >= ticksPeriod) {
            tick = 0
            channel.trySend(getter())
        }
    }
}


fun ItemAutosaveTimer(period: Long, itemStorage: ItemStorage, database: Database) = CoroutineQueueTimer(period, ItemIoCoroutineScope, { itemStorage.getAll() }) { items ->
    database.saveItemPersistentDataBatch(
        items.map { it to itemPersistentData(it) }
    )
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
    database.saveItemPersistentDataBatch(
        items.map { it to itemPersistentData(it) }
    )
    server.execute {
        items.forEach { itemStorage.remove(it.uuid) }
    }
}

class ItemLoader(
    private val database: Database,
    private val engine: EngineServer,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val items = mutableMapOf<ItemUuid, Long>()
    private val notFound = mutableSetOf<ItemUuid>()

    fun isLoading(item: ItemUuid) = item in items

    fun isNotFound(item: ItemUuid) = item in notFound

    fun loadItem(location: Location, uuid: ItemUuid) {
        val time = items[uuid]
        val currentTimeMillis = System.currentTimeMillis()
        if (time == null || currentTimeMillis - time > TIMEOUT) {
            items[uuid] = currentTimeMillis
            coroutineScope.launch {
                val item = database.loadItem(location, uuid)
                engine.execute {
                    if (item != null) {
                        engine.itemStorage.add(uuid, item)
                    } else {
                        notFound += uuid
                    }
                }
            }
        }
    }

    companion object {
        private const val TIMEOUT = 10 * 1000
    }
}