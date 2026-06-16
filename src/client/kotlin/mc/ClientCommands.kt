package org.lain.engine.client.mc

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.client.EngineClient
import org.lain.engine.mc.literalText
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
    }
}