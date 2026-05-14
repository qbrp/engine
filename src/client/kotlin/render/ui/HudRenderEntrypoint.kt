package org.lain.engine.client.render.ui

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import org.lain.cyberia.ecs.get
import org.lain.cyberia.ecs.handle
import org.lain.engine.client.EngineClient
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.ScreenRenderer
import org.lain.engine.client.render.legacy.EngineUiRenderPipeline
import org.lain.engine.mc.engineId
import org.lain.engine.player.InteractionComponent
import org.lain.engine.player.Narration

fun registerHudRenderEvent(
    client: Minecraft,
    engineClient: EngineClient,
    screenRenderer: ScreenRenderer,
    engineUiRenderPipeline: EngineUiRenderPipeline,
) {
    HudElementRegistry.addLast(
        engineId("ui")
    ) { context, tickCounter ->
        val deltaTick = tickCounter.realtimeDeltaTicks
        context.pose().pushMatrix()
        val window = MinecraftClient.window
        val mouse = MinecraftClient.mouseHandler
        screenRenderer.isFirstPerson = !client.gameRenderer.mainCamera.isDetached
        screenRenderer.chatOpen = client.screen is ChatScreen
        val mouseX = mouse.getScaledXPos(window)
        val mouseY = mouse.getScaledYPos(window)
        val mainPlayer = engineClient.gameSession?.mainPlayer
        if (mainPlayer != null) {
            mainPlayer.handle<Narration> {
                renderNarrations(
                    context,
                    screenRenderer.narrations,
                    this,
                    deltaTick
                )
            }
            renderInteractionProgression(
                context,
                screenRenderer.interactionProgression,
                mainPlayer.get<InteractionComponent>(),
                deltaTick
            )
            screenRenderer.renderScreen(deltaTick)
        }
        engineUiRenderPipeline.render(
            context,
            deltaTick,
            mouseX.toFloat(),
            mouseY.toFloat()
        )
        context.pose().popMatrix()
    }
}