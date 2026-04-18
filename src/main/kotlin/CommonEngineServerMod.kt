package org.lain.engine

import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.PlayerConfigEntry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.world.Difficulty
import net.minecraft.world.GameRules
import org.lain.engine.SharedConstants.DEVELOPER_TEST_ENVIRONMENT
import org.lain.engine.mc.*
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
                server.gameRules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server)
                server.setDifficulty(Difficulty.PEACEFUL, true)
            }
        }

        ServerWorldEvents.LOAD.register { server, world ->
            if (DEVELOPER_TEST_ENVIRONMENT) {
                world.timeOfDay = 0
            }
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { server ->
            engineServer.disable()
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            if (DEVELOPER_TEST_ENVIRONMENT) {
                server.playerManager.addToOperators(PlayerConfigEntry(handler.player.gameProfile))
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

        UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            if (world.isClient) return@register ActionResult.PASS
            val hitPlayer = hitResult?.entity ?: return@register ActionResult.PASS
            if (hitPlayer !is ServerPlayerEntity) return@register ActionResult.PASS
            player.sendMessage(
                Text.empty().apply {
                    val styled = hitPlayer.styledDisplayName
                    val name = hitPlayer.name
                    val similar = styled == name

                    append(if (similar) name else hitPlayer.styledDisplayName)

                    if (!similar) {
                        append(
                            Text.empty()
                                .append(" (${hitPlayer.name.string})")
                                .formatted(Formatting.GRAY)
                        )
                    }
                },
                true
            )
            ActionResult.PASS
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, env ->
            dispatcher.registerEngineCommands(env.dedicated)
            if (isWorldEditAvailable()) dispatcher.registerWorldEditCommands()
        }

        Injector.register(environment)
    }

    companion object {
        const val MOD_ID = "engine"
    }
}