package org.lain.engine

import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import org.lain.engine.mc.EngineItemReferenceComponent
import org.lain.engine.mc.EntityTable
import org.lain.engine.mc.registerEngineCommands
import org.lain.engine.util.Environment
import org.lain.engine.util.Injector

/**
 * Класс отвечает за объявление **общих** на выделенном клиенте и серверах событиях.
 * Здесь регистрируются команды и вызываются методы майнкрафт-сервера Engine.
 * **Ответственность за создание `EngineMinecraftServer` лежит на других классах.** Здесь он получается из DI-контейнера через `Injector`
 * @see DedicatedServerEngineMod
 */

class CommonEngineServerMod : ModInitializer {
    private val entityTable = EntityTable()
        .also { Injector.register(it) }
    private val engineServer: EngineMinecraftServer
        get() = Injector.resolve(EngineMinecraftServer::class)

    override fun onInitialize() {
        val environment = when (FabricLoader.getInstance().environmentType) {
            EnvType.CLIENT -> Environment.CLIENT
            EnvType.SERVER -> Environment.SERVER
        }
        EngineItemReferenceComponent.initialize()

        ServerLifecycleEvents.SERVER_STARTED.register {
            engineServer.run()
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { server ->
            engineServer.disable()
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            engineServer.onJoinPlayer(handler.player)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            engineServer.onLeavePlayer(handler.player)
        }

        ServerTickEvents.END_SERVER_TICK.register {
            engineServer.tick()
        }

        ServerChunkEvents.CHUNK_UNLOAD.register { world, chunk ->
            engineServer.onChunkUnload(world, chunk)
        }

        UseBlockCallback.EVENT.register { entity, world, hand, hitResult ->
            if (world.isClient) return@register ActionResult.PASS
            val blockPos = hitResult.blockPos
            val state = world.getBlockState(blockPos)
            engineServer.onPlayerBlockInteraction(entity, blockPos, state, world)
            ActionResult.PASS
        }

        UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            if (world.isClient) return@register ActionResult.PASS
            val hitPlayer = hitResult?.entity ?: return@register ActionResult.PASS
            if (hitPlayer !is ServerPlayerEntity) return@register ActionResult.PASS
            player.sendMessage(
                Text.empty()
                    .append(hitPlayer.styledDisplayName)
                    .append(
                        Text.empty()
                            .append(" (${hitPlayer.name.string})")
                            .formatted(Formatting.GRAY)
                    ),
                true
            )
            ActionResult.PASS
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.registerEngineCommands()
        }

        Injector.register(environment)
    }

    companion object {
        const val MOD_ID = "engine"
    }
}