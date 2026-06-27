package org.lain.engine.client.mc

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.shapes.CollisionContext
import org.lain.cyberia.ecs.exists
import org.lain.cyberia.ecs.getAll
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.client.EngineClient
import org.lain.engine.item.BULLET_FIRE_RADIUS
import org.lain.engine.mc.commands.getString
import org.lain.engine.mc.getWorld
import org.lain.engine.mc.literalText
import org.lain.engine.mc.toMinecraft
import org.lain.engine.mc.voxelPos
import org.lain.engine.player.handItem
import org.lain.engine.script.lua.LuaFunctionChunk
import org.lain.engine.script.lua.coerceToLua
import org.lain.engine.script.lua.toLuaValue
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.world.LightBehaviour
import org.lain.engine.world.LightSource
import org.lain.engine.world.Luminance
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue

fun registerClientEngineCommands(engineClient: EngineClient) {
    ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
        dispatcher.register(
            ClientCommandManager.literal("edclient")
                .then(
                    ClientCommandManager.literal("illuminate")
                        .executes { ctx ->
                            val gameSession = engineClient.gameSession ?: return@executes 0
                            with(gameSession.world) {
                                val entity = gameSession.mainPlayer.entityId
                                if (entity.hasComponent<LightSource>()) {
                                    entity.removeComponent<LightSource>()
                                    entity.removeComponent<Luminance>()
                                } else {
                                    entity.setComponent(LightSource(LightBehaviour.Sphere(6)))
                                    entity.setComponent(Luminance(14))
                                }
                            }
                            1
                        }
                )
        )
        dispatcher.register(
            ClientCommandManager.literal("scriptexecclient")
                .then(
                    ClientCommandManager.argument("statement", StringArgumentType.greedyString())
                        .executes { ctx ->
                            val gameSession = engineClient.gameSession ?: return@executes 0
                            try {
                                with(gameSession.luaContext) {
                                    val function = LuaFunctionChunk(
                                        StringArgumentType.getString(ctx, "statement"),
                                        "player", "world"
                                    )

                                    ctx.source.sendFeedback(
                                        literalText(
                                            function.execute(
                                                this,
                                                gameSession.mainPlayer.coerceToLua(), //player
                                                gameSession.world.coerceToLua() //world
                                            )
                                                .tojstring()
                                        )
                                    )
                                }
                            } catch (e: LuaError) {
                                ctx.source.sendError(literalText(e.message ?: "error"))
                            }
                            1
                        }
                )
        )
        dispatcher.register(
            ClientCommandManager.literal("blockhint")
                .then(
                    ClientCommandManager.literal("add")
                        .then(
                            ClientCommandManager.argument("text", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    val gameSession = engineClient.gameSession ?: return@executes 0
                                    val text = StringArgumentType.getString(ctx, "text")
                                    val player = ctx.source.player ?: return@executes 0
                                    val world = ctx.source.world ?: return@executes 0

                                    val start = player.eyePosition
                                    val end = start.add(player.getViewVector(0f).scale(8.0))
                                    val results = world.clip(
                                        ClipContext(
                                            start,
                                            end,
                                            ClipContext.Block.COLLIDER,
                                            ClipContext.Fluid.NONE,
                                            CollisionContext.empty()
                                        )
                                    )

                                    if (world.getBlockState(results.blockPos).isAir) {
                                        ctx.source.sendError(literalText("Вы не смотрите на блок!"))
                                        return@executes 0
                                    }

                                    gameSession.handler.onBlockHintAdd(results.blockPos.voxelPos(), text)
                                    ctx.source.sendFeedback(literalText("Добавлено описание: $text"))
                                    1
                                }
                        )
                )
                .then(
                    ClientCommandManager.literal("remove")
                        .then(
                            ClientCommandManager.argument("index", IntegerArgumentType.integer())
                                .executes { ctx ->
                                    val gameSession = engineClient.gameSession ?: return@executes 0
                                    val index = IntegerArgumentType.getInteger(ctx, "index")
                                    val player = ctx.source.player ?: return@executes 0
                                    val world = ctx.source.world ?: return@executes 0

                                    val start = player.eyePosition
                                    val end = start.add(player.getViewVector(0f).scale(8.0))
                                    val results = world.clip(
                                        ClipContext(
                                            start,
                                            end,
                                            ClipContext.Block.COLLIDER,
                                            ClipContext.Fluid.NONE,
                                            CollisionContext.empty()
                                        )
                                    )

                                    val voxelPos = results.blockPos.voxelPos()
                                    val hint = gameSession.world.chunkStorage.getBlockHint(voxelPos)

                                    if (world.getBlockState(results.blockPos).isAir) {
                                        ctx.source.sendError(literalText("Вы не смотрите на блок!"))
                                        return@executes 0
                                    } else if (hint == null) {
                                        ctx.source.sendError(literalText("У блока нет описания!"))
                                        return@executes 0
                                    } else if (hint.texts.size - 1 < index) {
                                        val size = hint.texts.size - 1
                                        val builder = StringBuilder("У блока нет описания под индексом $index! ")
                                        if (size == 0) {
                                            builder.append("Доступен только индекс 0.")
                                        } else {
                                            builder.append("Доступен диапазон: 0-$size.")
                                        }
                                        ctx.source.sendError(literalText(builder.toString()))
                                        return@executes 0
                                    }

                                    val text = hint.texts[index]

                                    gameSession.handler.onBlockHintRemove(results.blockPos.voxelPos(), index)
                                    ctx.source.sendFeedback(literalText("Удалено описание под индексом $index: $text"))
                                    1
                                }
                        )
                )
        )

        dispatcher.register(
            ClientCommandManager.literal("enginedebug")
                .then(
                    ClientCommandManager.literal("block")
                        .executes { ctx ->
                            val gameSession = engineClient.gameSession ?: return@executes 0
                            val world = ctx.source.world ?: return@executes 0
                            val hitResult = ctx.source.client.blockHitResult ?: return@executes 0

                            val blockPos = hitResult.blockPos
                            val voxel = gameSession.world.chunkStorage.getDynamicVoxel(blockPos.voxelPos())
                            if (world.getBlockState(blockPos).isAir) {
                                ctx.source.sendError(literalText("Вы не смотрите на блок!"))
                                return@executes 0
                            } else if (voxel == null) {
                                ctx.source.sendError(literalText("Блок не является сущностью!"))
                                return@executes 0
                            }

                            gameSession.viewEntityDebug(voxel)
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("item")
                        .executes { ctx ->
                            val gameSession = engineClient.gameSession ?: return@executes 0
                            val item = gameSession.mainPlayer.handItem ?: run {
                                ctx.source.sendError(literalText("Вы не держите предмет!"))
                                return@executes 0
                            }
                            gameSession.viewEntityDebug(item)
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("self")
                        .executes { ctx ->
                            val gameSession = engineClient.gameSession ?: return@executes 0
                            gameSession.viewEntityDebug(gameSession.mainPlayer.entityId)
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("entity")
                        .then(
                            ClientCommandManager.argument("id", IntegerArgumentType.integer(0))
                                .executes { ctx ->
                                    val gameSession = engineClient.gameSession ?: return@executes 0
                                    val entity = IntegerArgumentType.getInteger(ctx, "id")

                                    with(gameSession.world) {
                                        if (!entity.exists()) {
                                            ctx.source.sendError(literalText("Сущность под идентификатором $entity не существует!"))
                                        } else if (!entity.hasComponent<PersistentIdComponent>()) {
                                            ctx.source.sendError(literalText("Сущность не существует на стороне сервера!"))
                                        } else {
                                            gameSession.viewEntityDebug(entity)
                                        }
                                    }
                                    1
                                }
                        )
                )
        )
    }
}