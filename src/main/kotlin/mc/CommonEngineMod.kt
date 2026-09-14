package org.lain.engine.mc

import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.GameRules
import org.lain.engine.Constants
import org.lain.engine.bootstrap
import org.lain.engine.mc.commands.WORLD_EDIT_AVAILABLE
import org.lain.engine.mc.commands.registerEngineCommands
import org.lain.engine.mc.commands.registerWorldEditCommands
import org.lain.engine.mc.ecs.initializeEngineItemComponents
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.player.RaycastProvider
import org.lain.engine.util.Environment
import org.lain.engine.util.Injector
import org.lain.engine.util.file.FileSystem
import org.lain.engine.util.requireEngineMinecraftServer

/**
 * Класс отвечает за объявление **общих** на выделенном клиенте и серверах событиях.
 * Здесь регистрируются команды и вызываются методы майнкрафт-сервера Engine.
 * **Ответственность за создание `EngineMinecraftServer` лежит на других классах.** Здесь он получается из DI-контейнера через `Injector`
 * @see org.lain.engine.mc.server.DedicatedServerEngineMod
 */

class CommonEngineMod : ModInitializer {
    private val engineServer: EngineMinecraftServer?
        get() = Injector.server

    override fun onInitialize() {
        bootstrap()
        val environment = when (FabricLoader.getInstance().environmentType) {
            EnvType.CLIENT -> Environment.CLIENT
            EnvType.SERVER -> Environment.SERVER
        }
        FileSystem.migrateLegacyStructure()
        FileSystem.loadStandardLuaLibrary()
        initializeEngineItemComponents()

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            if (Constants.DEVELOPER_TEST_ENVIRONMENT) {
                val gameRules = server.worldData.gameRules
                gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, server)
                server.setDifficulty(Difficulty.PEACEFUL, true)
            }
            Injector.register<RaycastProvider>(MinecraftRaycastProvider())
            engineServer?.run()
        }

        ServerTickEvents.START_SERVER_TICK.register {
            engineServer?.tick()
        }

        ServerWorldEvents.LOAD.register { server, world ->
            if (Constants.DEVELOPER_TEST_ENVIRONMENT) {
                world.dayTime = 0
            }
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { server ->
            engineServer?.disable()
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            if (Constants.DEVELOPER_TEST_ENVIRONMENT) {
                server.playerList.op(handler.player.gameProfile)
            }
            engineServer?.onJoinPlayer(handler.player)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
            server.execute { engineServer?.onLeavePlayer(handler.player) }
        }

        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
            replacePlayerMinecraftState(newPlayer)
        }

        ServerChunkEvents.CHUNK_UNLOAD.register { world, chunk ->
            engineServer?.onChunkUnload(world, chunk)
        }

        ServerWorldEvents.UNLOAD.register { server, world ->
            engineServer?.onWorldUnload(world)
        }

        UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            if (world.isClientSide) return@register InteractionResult.PASS
            val hitPlayer = hitResult?.entity ?: return@register InteractionResult.PASS
            if (hitPlayer !is ServerPlayer || player !is ServerPlayer) return@register InteractionResult.PASS
            val enginePlayer = hitPlayer.getEngineState() ?: return@register InteractionResult.PASS
            with(enginePlayer.world) { showPlayerHint(player, enginePlayer) }
            InteractionResult.PASS
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, env ->
            dispatcher.registerEngineCommands(env.includeDedicated)
            if (WORLD_EDIT_AVAILABLE) dispatcher.registerWorldEditCommands()
        }

        Injector.register(environment)
    }

    companion object {
        const val MOD_ID = "engine"
    }
}
