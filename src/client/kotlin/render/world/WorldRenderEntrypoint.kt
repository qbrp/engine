package org.lain.engine.client.render.world

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.Minecraft
import org.lain.engine.client.EngineClient
import org.lain.engine.client.MinecraftEngineClientPlatform
import org.lain.engine.client.mc.ImmediateVertexConsumers
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.player.extractPlayerRenderState
import org.lain.engine.mc.square
import org.lain.engine.mc.voxelPos
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.pos

private val textCache = TextCache()

fun registerWorldRenderEvents(
    client: Minecraft,
    engineClient: EngineClient,
    eventBus: MinecraftEngineClientPlatform,
    decalsStorage: DecalSystem
) {
    val labels = WorldLabelRenderPass()
    ClientLifecycleEvents.CLIENT_STOPPING.register { labels.close() }

    WorldRenderEvents.AFTER_ENTITIES.register { renderContext ->
        val gameSession = engineClient.gameSession ?: return@register
        val matrices = renderContext.matrixStack() ?: return@register
        val vertexConsumers = renderContext.consumers() as? ImmediateVertexConsumers ?: return@register
        val camera = renderContext.camera()
        val cameraPos = camera.position
        val images = decalsStorage.getBlockImages(
            gameSession.mainPlayer.pos,
            MinecraftClient.options.renderDistance().get()
        )
        matrices.pushPose()
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)
        for ((pos, image) in images) {
            renderBlockDecals(image.gameTexture, pos, matrices, vertexConsumers)
        }
        matrices.popPose()
    }

    WorldRenderEvents.LAST.register { renderContext ->
        val gameSession = engineClient.gameSession ?: return@register
        val matrices = renderContext.matrixStack() ?: return@register
        val camera = renderContext.camera()
        val playerBlockPos = client.player?.blockPosition() ?: return@register

        val context = ImmediateWorldRenderContext(
            labels,
            client.font,
            matrices,
            screenRenderer = engineClient.renderer
        )
        val deltaTicks = renderContext.tickCounter().realtimeDeltaTicks
        try {
            with(context) {
                val acousticDebugVolumes = gameSession.acousticDebugVolumes
                if (engineClient.developerMode && engineClient.acousticDebug && acousticDebugVolumes.isNotEmpty()) {
                    renderAcousticDebugLabels(
                        eventBus.acousticDebugVolumesBlockPosCache,
                        listOf(playerBlockPos, playerBlockPos.offset(0, 1, 0)),
                        gameSession.vocalRegulator.volume.base,
                        gameSession.vocalRegulator.volume.max,
                        camera
                    )
                }

                val options = engineClient.options

                if ((!engineClient.renderer.hudHidden || gameSession.photoMode?.showChatBubbles != false) && options.chatBubbles) {
                    renderChatBubbles(
                        camera,
                        options.labelEasingDistance.toFloat(),
                        options.chatBubbleScale,
                        options.chatBubbleHeight,
                        options.chatBubbleBackgroundOpacity,
                        gameSession.chatBubbleList.bubbles,
                        options.chatBubbleIgnoreLightLevel,
                        deltaTicks
                    )
                }

                if (!engineClient.renderer.hudHidden) {
                    val visibleBlockHintChunks = EngineChunkPos(playerBlockPos.voxelPos()).square(1)
                    val visibleBlockHints = buildMap {
                        visibleBlockHintChunks
                            .mapNotNull { gameSession.world.chunkStorage.getChunk(it) }
                            .forEach { putAll(it.hints) }
                    }
                    renderBlockHints(
                        camera,
                        gameSession.hintState,
                        gameSession.inspection,
                        gameSession.inspectionMode,
                        300,
                        visibleBlockHints,
                        textCache,
                        deltaTicks
                    )
                }
            }
            labels.render(client)
        } finally {
            labels.discard()
        }
    }
}
