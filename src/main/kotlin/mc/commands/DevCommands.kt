package org.lain.engine.mc.commands

import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.world.entity.player.Player
import org.lain.engine.item.bakeInvalidProtoItem
import org.lain.engine.item.instantiateItem
import org.lain.engine.mc.ITEM_STACK_MATERIAL
import org.lain.engine.mc.wrapEngineItemStack
import org.lain.engine.script.LOGGER
import org.lain.engine.script.lua.coerceToLua
import org.lain.engine.script.lua.toLuaValue
import org.lain.engine.storage.saveItemsBlocking
import org.lain.engine.util.getServerStats
import org.lain.engine.util.injectMinecraftEngineServer
import org.lain.engine.world.world
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue

fun ServerCommandDispatcher.registerEngineDeveloperCommands() {
    val server by injectMinecraftEngineServer()
    val playerTable by lazy { server.entityTable }
    val engine by lazy { server.engine }
    register(
        literal("scriptexec")
            .requires { it.hasPermission("scriptexec") }
            .then(
                argument("statement", StringArgumentType.greedyString())
                    .executeCatching {
                        try {
                            with(server.luaContext) {
                                val value = globals.load(it.command.getString("statement")).call(
                                    "player".toLuaValue(), it.player?.coerceToLua() ?: LuaValue.NIL
                                )
                                it.sendFeedback(value.tojstring(), false)
                            }
                        } catch (e: LuaError) {
                            it.sendError(e)
                            e.cause?.let { cause -> it.sendError("caused by: ${cause.message}") }
                            LOGGER.error("Ошибка выполнения scriptexec", e)
                        }
                    }
            )
    )
    register(
        literal("ed")
            .requires { it.hasPermission("ed") }
            .then(
                literal("positions")
                    .executeCatching { ctx ->
                        val lines = playerPositionsMessage(engine.playerStorage, ctx.source.level)
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
                            engine.bakeInvalidProtoItem(world),
                            engine.itemStorage
                        )
                        val entity = ctx.requireEntity() as? Player ?: return@executeCatching
                        val itemStack = ITEM_STACK_MATERIAL.copy()
                        with(world) { wrapEngineItemStack(item, itemStack) }
                        entity.addItem(itemStack)
                    }
            )
    )
}