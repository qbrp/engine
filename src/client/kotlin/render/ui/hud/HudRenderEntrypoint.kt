package org.lain.engine.client.render.ui.hud

import foundry.imgui.api.ImGuiMC
import foundry.imgui.api.ImGuiMCEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import org.lain.cyberia.ecs.getComponent
import org.lain.engine.client.EngineClient
import org.lain.engine.client.render.ScreenRenderer
import org.lain.engine.client.render.ui.renderInteractionProgression
import org.lain.engine.client.render.ui.renderNarrations
import org.lain.engine.player.Narration
import org.lain.engine.player.handle
import org.lain.engine.player.interaction.Progression

fun registerHudRenderEvent(
    client: Minecraft,
    engineClient: EngineClient,
    screenRenderer: ScreenRenderer,
) {
    HudRenderCallback.EVENT.register { context, tickCounter ->
        val deltaTick = tickCounter.realtimeDeltaTicks
        context.pose().pushPose()
        screenRenderer.isFirstPerson = !client.gameRenderer.mainCamera.isDetached
        screenRenderer.chatOpen = client.screen is ChatScreen
        val gameSession = engineClient.gameSession
        val mainPlayer = gameSession?.mainPlayer
        screenRenderer.renderScreen(deltaTick)
        if (gameSession != null && mainPlayer != null) {
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
                with(mainPlayer.world) { mainPlayer.entity.getComponent<Progression>() },
                deltaTick
            )
            renderMovementStatus(
                context,
                screenRenderer.movementStatus,
                gameSession,
                deltaTick
            )
        }
        screenRenderer.littleNotificationsRenderer.render(context)

        context.pose().popPose()
    }
}
