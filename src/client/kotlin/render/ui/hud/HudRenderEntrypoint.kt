package org.lain.engine.client.render.ui.hud

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import org.lain.cyberia.ecs.getComponent
import org.lain.engine.client.EngineClient
import org.lain.engine.client.render.ScreenRenderer
import org.lain.engine.client.render.ui.renderInteractionProgression
import org.lain.engine.player.handle
import org.lain.engine.player.interaction.Progression

fun registerHudRenderEvent(
    client: Minecraft,
    screenRenderer: ScreenRenderer,
) {
    HudRenderCallback.EVENT.register { context, tickCounter ->
        val deltaTick = tickCounter.realtimeDeltaTicks
        context.pose().pushPose()
        screenRenderer.isFirstPerson = !client.gameRenderer.mainCamera.isDetached
        screenRenderer.chatOpen = client.screen is ChatScreen
        screenRenderer.renderScreen(context, deltaTick)
        context.pose().popPose()
    }
}
