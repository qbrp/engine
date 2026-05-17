package org.lain.engine

import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.NameAndId
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.gamerules.GameRules
import org.lain.cyberia.ecs.require
import org.lain.engine.Constants.DEVELOPER_TEST_ENVIRONMENT
import org.lain.engine.mc.*
import org.lain.engine.mc.commands.registerEngineCommands
import org.lain.engine.player.DisplayName
import org.lain.engine.player.RaycastProvider
import org.lain.engine.util.Environment
import org.lain.engine.util.Injector
import org.lain.engine.util.file.loadStdLuaLibrary
import org.lain.engine.util.file.updateOldFileNaming
import org.lain.engine.util.injectValue

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
        bootstrap()
        val environment = when (FabricLoader.getInstance().environmentType) {
            EnvType.CLIENT -> Environment.CLIENT
            EnvType.SERVER -> Environment.SERVER
        }
        updateOldFileNaming()
        loadStdLuaLibrary()
        initializeEngineItemComponents()

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            Injector.register<RaycastProvider>(MinecraftRaycastProvider(injectValue()))
            engineServer.run()
            if (DEVELOPER_TEST_ENVIRONMENT) {
                val gameRules = server.worldData.gameRules
                gameRules.set(GameRules.ADVANCE_TIME, false, server)
                server.setDifficulty(Difficulty.PEACEFUL, true)
            }
        }

        ServerWorldEvents.LOAD.register { server, world ->
            if (DEVELOPER_TEST_ENVIRONMENT) {
                world.dayTime = 0
            }
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { server ->
            engineServer.disable()
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            if (DEVELOPER_TEST_ENVIRONMENT) {
                server.playerList.op(NameAndId(handler.player.gameProfile))
            }
            engineServer.onJoinPlayer(handler.player)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
            server.execute { engineServer.onLeavePlayer(handler.player) }
        }

        ServerTickEvents.START_SERVER_TICK.register {
            engineServer.tick()
        }

        ServerChunkEvents.CHUNK_UNLOAD.register { world, chunk ->
            engineServer.onChunkUnload(world, chunk)
        }

        ServerWorldEvents.UNLOAD.register { server, world ->
            engineServer.onWorldUnload(world)
        }

        UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            if (world.isClientSide) return@register InteractionResult.PASS
            val hitPlayer = hitResult?.entity ?: return@register InteractionResult.PASS
            if (hitPlayer !is ServerPlayer) return@register InteractionResult.PASS
            val enginePlayer = entityTable.getGeneralPlayer(hitPlayer) ?: return@register InteractionResult.PASS

            val name = enginePlayer.require<DisplayName>()
            val username = name.username.value
            val customName = name.custom

            val message = StringBuilder()
            message.append(customName?.textMiniMessage ?: username)
            if (customName != null) { message.append(" <gray>($username)</gray>") }

            player.sendActionBarMessage(message.toString())
            InteractionResult.PASS
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, env ->
            dispatcher.registerEngineCommands(env.includeDedicated)
            if (isWorldEditAvailable()) dispatcher.registerWorldEditCommands()
        }

        Injector.register(environment)
    }

    companion object {
        const val MOD_ID = "engine"
    }
}