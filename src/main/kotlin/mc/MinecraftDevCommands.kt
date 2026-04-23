package org.lain.engine.mc

import net.minecraft.entity.player.PlayerEntity
import org.lain.engine.item.instantiateItem
import org.lain.engine.storage.InvalidItem
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.saveItemsBlocking
import org.lain.engine.util.getServerStats
import org.lain.engine.util.injectMinecraftEngineServer
import org.lain.engine.world.world

fun ServerCommandDispatcher.registerEngineDeveloperCommands() {
    val server by injectMinecraftEngineServer()
    val playerTable by lazy { server.entityTable }
    val engine by lazy { server.engine }
    register(
        literal("ed")
            .requires {
                val player = it.player
                player != null && player.isOp
            }
            .then(
                literal("positions")
                    .executeCatching { ctx ->
                        val lines = playerPositionsMessage(engine.playerStorage, ctx.source.world)
                        lines.forEach { ctx.sendFeedback(it, false) }
                    }
            )
            .then(
                literal("ticks")
                    .executeCatching { ctx ->
                        val stats = getServerStats(engine.tickTimes.toList())
                        ctx.sendFeedback("Средняя длительность последних 20 тактов engine: ${stats.averageTickTimeMillis} мл.", false)
                    }
            )
            .then(
                literal("save-items")
                    .executeCatching { ctx ->
                        val world = ctx.requirePlayer().world
                        val count = server.database.saveItemsBlocking(world)
                        ctx.sendFeedback("Вызвано блокирующее сохранение $count предметов", true)
                    }
            )
            .then(
                literal("save-items-timer")
                    .executeCatching { ctx ->
                        server.timers.items.activate()
                        ctx.sendFeedback("Вызвано сохранение предметов", true)
                    }
            )
            .then(
                literal("invalid-item")
                    .executeCatching { ctx ->
                        val player = ctx.requirePlayer()
                        val world = player.world
                        val item = world.instantiateItem(
                            InvalidItem(PersistentId.next(), world),
                            engine.itemStorage
                        )
                        val entity = ctx.requireEntity() as? PlayerEntity ?: return@executeCatching
                        val itemStack = ITEM_STACK_MATERIAL.copy()
                        with(world) { wrapEngineItemStack(item, itemStack) }
                        entity.giveItemStack(itemStack)
                    }
            )
    )
}