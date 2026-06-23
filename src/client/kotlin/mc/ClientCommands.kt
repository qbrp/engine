package org.lain.engine.client.mc

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.shapes.CollisionContext
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.client.EngineClient
import org.lain.engine.item.BULLET_FIRE_RADIUS
import org.lain.engine.mc.literalText
import org.lain.engine.mc.toMinecraft
import org.lain.engine.mc.voxelPos
import org.lain.engine.script.lua.coerceToLua
import org.lain.engine.script.lua.toLuaValue
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
                                    val value = globals.load(StringArgumentType.getString(ctx, "statement")).call(
                                        "player".toLuaValue(), gameSession.mainPlayer.coerceToLua()
                                    )
                                    ctx.source.sendFeedback(literalText(value.tojstring()))
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

                                    val start = player.position()
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
                                    1
                                }
                        )
                )
                .then(
                    ClientCommandManager.literal("remove")
                )
        )
    }
}