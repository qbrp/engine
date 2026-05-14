package org.lain.engine.client.mc.render.world

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.Minecraft
import org.lain.engine.client.EngineClient
import org.lain.engine.client.MinecraftEngineClientEventBus
import org.lain.engine.client.mc.ImmediateVertexConsumers
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.mc.EntityTable
import org.lain.engine.util.injectEntityTable
import org.lain.engine.world.pos

fun registerWorldRenderEvents(
    client: Minecraft,
    engineClient: EngineClient,
    eventBus: MinecraftEngineClientEventBus,
    decalsStorage: DecalSystem,
    playerTable: EntityTable,
) {
    WorldRenderEvents.END_MAIN.register { context ->
        val gameRenderer = context.gameRenderer()
        val camera = gameRenderer.mainCamera
        val cameraPos = camera.position()
        val matrices = context.matrices()
        val queue = context.commandQueue()

        val gameSession = engineClient.gameSession
        val acousticDebugVolumes = gameSession?.acousticDebugVolumes
        val playerBlockPos = client.player?.blockPosition() ?: return@register
        if (engineClient.developerMode && engineClient.acousticDebug && gameSession != null && acousticDebugVolumes?.isNotEmpty() == true) {
            renderAcousticDebugLabels(
                eventBus.acousticDebugVolumesBlockPosCache,
                listOf(playerBlockPos, playerBlockPos.offset(0, 1, 0)),
                gameSession.vocalRegulator.volume.base,
                gameSession.vocalRegulator.volume.max,
                queue,
                matrices,
                gameRenderer.levelRenderState.cameraRenderState
            )
        }

        val vertexConsumers = context.consumers()
        if (vertexConsumers !is ImmediateVertexConsumers) return@register

        val entityTable by injectEntityTable()
        val context = ImmediateWorldRenderContext(entityTable, vertexConsumers, client.font, matrices)
        with(context) {
            val options = engineClient.options
            if (!(options.hideChatBubblesWithUi && engineClient.renderer.hudHidden) && options.chatBubbles) {
                renderChatBubbles(
                    camera,
                    options.labelEasingDistance.toFloat(),
                    options.chatBubbleScale,
                    options.chatBubbleHeight,
                    options.chatBubbleBackgroundOpacity,
                    gameSession?.chatBubbleList?.bubbles ?: emptyList(),
                    options.chatBubbleIgnoreLightLevel,
                    client.deltaTracker.realtimeDeltaTicks
                )
            }
        }
    }

    WorldRenderEvents.BEFORE_ENTITIES.register { context ->
        val matrices = context.matrices()
        val queue = context.commandQueue()
        val camera = context.gameRenderer().mainCamera

        val gameSession = engineClient.gameSession
        if (gameSession != null) {
            gameSession.updatePlayerEntityRenderStates(playerTable)
            val images = decalsStorage.getBlockImages(
                engineClient.gameSession?.mainPlayer?.pos ?: return@register,
                MinecraftClient.options.renderDistance().get()
            )
            matrices.pushPose()
            matrices.translate(camera.position().reverse())
            for ((pos, image) in images) {
                renderBlockDecals(image.gameTexture, pos, matrices, queue)
            }
            matrices.popPose()
        }
    }
}