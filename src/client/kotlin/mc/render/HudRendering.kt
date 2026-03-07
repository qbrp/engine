package org.lain.engine.client.mc.render

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ChatScreen
import org.lain.engine.client.EngineClient
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.ScreenRenderer
import org.lain.engine.player.InteractionComponent
import org.lain.engine.player.Narration
import org.lain.engine.util.EngineId
import org.lain.engine.util.component.get
import org.lain.engine.util.component.handle

fun registerHudRenderEvent(
    client: MinecraftClient,
    engineClient: EngineClient,
    fontRenderer: MinecraftFontRenderer,
    screenRenderer: ScreenRenderer,
    engineUiRenderPipeline: EngineUiRenderPipeline
) {
    HudElementRegistry.addLast(
        EngineId("ui")
    ) { context, tickCounter ->
        val deltaTick = tickCounter.fixedDeltaTicks
        val painter = MinecraftPainter(
            deltaTick,
            context,
            fontRenderer
        )
        context.matrices.pushMatrix()
        screenRenderer.isFirstPerson = !client.gameRenderer.camera.isThirdPerson
        screenRenderer.chatOpen = client.currentScreen is ChatScreen
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
            screenRenderer.renderScreen(painter)
        }
        val window = MinecraftClient.window
        val mouse = MinecraftClient.mouse
        engineUiRenderPipeline.render(
            context,
            deltaTick,
            mouse.getScaledX(window).toFloat(),
            mouse.getScaledY(window).toFloat()
        )
        context.matrices.popMatrix()
    }
}