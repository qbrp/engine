package org.lain.engine.client.render.world

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.Minecraft
import org.lain.engine.client.EngineClient
import org.lain.engine.client.MinecraftEngineClientPlatform
import org.lain.engine.client.mc.ImmediateVertexConsumers
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.legacy.TextCache
import org.lain.engine.client.render.player.updatePlayerEntityRenderStates
import org.lain.engine.mc.square
import org.lain.engine.mc.voxelPos
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.pos

private val TextCache = TextCache()

fun registerWorldRenderEvents(
    client: Minecraft,
    engineClient: EngineClient,
    eventBus: MinecraftEngineClientPlatform,
    decalsStorage: DecalSystem
) {
    WorldRenderEvents.BEFORE_ENTITIES.register {
        engineClient.gameSession?.updatePlayerEntityRenderStates()
    }

    WorldRenderEvents.AFTER_ENTITIES.register { renderContext ->
        val gameSession = engineClient.gameSession ?: return@register
        val matrices = renderContext.matrixStack() ?: return@register
        val vertexConsumers = renderContext.consumers() as? ImmediateVertexConsumers ?: return@register
        val camera = renderContext.camera()
        val cameraPos = camera.position
        val playerBlockPos = client.player?.blockPosition() ?: return@register

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

        val context = ImmediateWorldRenderContext(
            vertexConsumers,
            client.font,
            matrices,
            screenRenderer = engineClient.renderer
        )
        val deltaTicks = renderContext.tickCounter().realtimeDeltaTicks
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
            if (!engineClient.renderer.hudHidden) {
                if (!options.hideChatBubblesWithUi && options.chatBubbles) {
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
                val visibleBlockHintChunks = EngineChunkPos(playerBlockPos.voxelPos()).square(1)
                visibleBlockHintChunks
                    .mapNotNull { gameSession.world.chunkStorage.getChunk(it) }
                    .forEach {
                        renderBlockHints(
                            camera,
                            gameSession.hintState,
                            gameSession.inspection,
                            gameSession.inspectionMode,
                            300,
                            it.hints,
                            TextCache,
                            deltaTicks
                        )
                    }
            }
        }
    }
}
