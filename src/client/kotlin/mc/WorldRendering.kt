package org.lain.engine.client.mc

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumerProvider
import org.lain.engine.client.EngineClient
import org.lain.engine.client.MinecraftEngineClientEventBus
import org.lain.engine.client.mc.ClientMixinAccess.renderChatBubbles
import org.lain.engine.client.mc.render.ChunkDecalsStorage
import org.lain.engine.client.mc.render.renderAcousticDebugLabels
import org.lain.engine.client.mc.render.renderBlockDecals
import org.lain.engine.world.pos

fun registerWorldRenderEvents(client: MinecraftClient, engineClient: EngineClient, eventBus: MinecraftEngineClientEventBus, decalsStorage: ChunkDecalsStorage) {
    WorldRenderEvents.END_MAIN.register { context ->
        val gameRenderer = context.gameRenderer()
        val camera = gameRenderer.camera
        val cameraPos = camera.pos
        val matrices = context.matrices()
        val queue = context.commandQueue()

        val gameSession = engineClient.gameSession
        val acousticDebugVolumes = gameSession?.acousticDebugVolumes
        val playerBlockPos = client.player?.blockPos ?: return@register
        if (engineClient.developerMode && engineClient.acousticDebug && gameSession != null && acousticDebugVolumes?.isNotEmpty() == true) {
            renderAcousticDebugLabels(
                eventBus.acousticDebugVolumesBlockPosCache,
                listOf(playerBlockPos, playerBlockPos.add(0, 1, 0)),
                gameSession.vocalRegulator.volume.base,
                gameSession.vocalRegulator.volume.max,
                queue,
                matrices,
                gameRenderer.entityRenderStates.cameraRenderState
            )
        }

        val vertexConsumers = context.consumers()
        if (vertexConsumers !is VertexConsumerProvider.Immediate) return@register
        renderChatBubbles(matrices, camera, vertexConsumers, cameraPos.x, cameraPos.y, cameraPos.z)
    }

    WorldRenderEvents.BEFORE_ENTITIES.register { context ->
        val matrices = context.matrices()
        val queue = context.commandQueue()
        val camera = context.gameRenderer().camera
        val images = decalsStorage.getBlockImages(
            engineClient.gameSession?.mainPlayer?.pos ?: return@register,
            MinecraftClient.options.viewDistance.value
        )

        matrices.push()
        matrices.translate(camera.pos.negate())
        for ((pos, image) in images) {
            renderBlockDecals(image.gameTexture, pos, matrices, queue)
        }
        matrices.pop()
    }
}