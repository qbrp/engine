package org.lain.engine.mc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import net.minecraft.world.item.ItemStack
import org.lain.engine.item.EngineItem
import org.lain.engine.item.createInvalidItem
import org.lain.engine.server.EngineServer
import org.lain.engine.storage.ItemLoadContext
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.dataFixItem
import org.lain.engine.util.EngineLogger
import org.lain.engine.util.Log
import org.lain.engine.util.LogLevel
import org.lain.engine.util.LogMessages
import org.lain.engine.world.World

private val ItemStackIoCoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

data class EngineItemStack(val engineItem: EngineItem, val itemStack: ItemStack)

// TODO: в будущем, в теории, можно организовать структуру по другому, чтобы не дублировать одинаковые переменные
// Конкретнее: сделать sealed class Holder, и от него PlayerInventory, PropInventory, VoxelInventory и т.д.
data class NotLoadedEngineItemStack(
    val world: World,
    val itemUuid: PersistentId,
    val itemStack: ItemStack,
    val referenceComponent: EngineItemReferenceComponent,
    val context: ItemLoadContext.FromInventory
)

fun updateMinecraftItemLoadSystem(
    itemStacksToLoad: MutableList<NotLoadedEngineItemStack>,
    engine: EngineServer
) {
    if (itemStacksToLoad.isNotEmpty()) {
        ItemStackIoCoroutineScope.launch {
            val semaphore = Semaphore(16)
            itemStacksToLoad
                .map { data ->
                    val world = data.world
                    async {
                        semaphore.withPermit {
                            try {
                                val item = withTimeout(5000) { engine.itemLoader.loadWorldItem(data.itemUuid, world, data.context) }
                                context(world) {
                                    engine.execute {
                                        try {
                                            dataFixItem(item, engine.namespacedStorage)
                                            wrapEngineItemStack(item, data.itemStack)
                                        } catch (e: Throwable) {
                                            EngineLogger.log(
                                                Log(
                                                    LogMessages.ITEM_STACK_INIT_ERROR,
                                                    LogLevel.ERROR,
                                                    error = e,
                                                    data = data.context.data(),
                                                    tick = engine.tick,
                                                    world = world.id
                                                )
                                            )
                                            val item = engine.createInvalidItem(world)
                                            wrapEngineItemStack(item, data.itemStack)
                                        }
                                    }
                                }
                            } finally {
                                engine.execute {
                                    data.referenceComponent.loading = false
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }
    }
}