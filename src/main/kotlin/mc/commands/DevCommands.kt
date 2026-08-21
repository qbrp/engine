package org.lain.engine.mc.commands

import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.world.entity.player.Player
import org.lain.engine.item.createInvalidItem
import org.lain.engine.mc.ITEM_STACK_MATERIAL
import org.lain.engine.mc.getWorld
import org.lain.engine.mc.wrapEngineItemStack
import org.lain.engine.script.SCRIPT_LOGGERRR
import org.lain.engine.script.lua.LuaFunctionChunk
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.script.lua.library.luaWorld
import org.lain.engine.storage.saveItemsBlocking
import org.lain.engine.util.getServerStats
import org.lain.engine.util.requireEngineMinecraftServer
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue

fun ServerCommandDispatcher.registerEngineDeveloperCommands() {
    val server by lazy { requireEngineMinecraftServer() }
    val engine by lazy { server.engine }
    register(
        literal("scriptexec")
            .requires { it.hasPermission("scriptexec") }
            .then(
                argument("statement", StringArgumentType.greedyString())
                    .executeCatching {
                        val luaContext = engine.luaScriptEngine
                        with(luaContext) {
                            try {
                                val function = LuaFunctionChunk(
                                    it.command.getString("statement"),
                                    "player", "world"
                                )
                                it.sendFeedback(
                                    function.execute(
                                        luaContext,
                                        (it.player?.coerceToLua() ?: LuaValue.NIL), //player
                                        (it.player?.world ?: engine.getWorld(it.source.level)).luaWorld() //world
                                    )
                                        .tojstring(),
                                    false
                                )
                            } catch (e: LuaError) {
                                it.sendError(e)
                                e.cause?.let { cause -> it.sendError("caused by: ${cause.message}") }
                                SCRIPT_LOGGERRR.error("Ошибка выполнения scriptexec", e)
                            }
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
                        val item = server.engine.createInvalidItem(world)
                        val entity = ctx.requireEntity() as? Player ?: return@executeCatching
                        val itemStack = ITEM_STACK_MATERIAL.copy()
                        with(world) { wrapEngineItemStack(item, itemStack) }
                        entity.addItem(itemStack)
                    }
            )
    )
}