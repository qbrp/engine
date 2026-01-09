package org.lain.engine.client.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.GameTestDebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.mc.render.ChatBubbleRenderKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameTestDebugRenderer.class)
public class GameTestDebugRendererMixin {
    @Inject(method = "renderMarkers", at = @At("RETURN"))
    private void onRenderMarkers(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null && client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
                Camera camera = client.gameRenderer.getCamera();
                Vec3d cameraPos = camera.getPos();
                ClientMixinAccess.INSTANCE.renderChatBubbles(matrices, camera, immediate, cameraPos.x, cameraPos.y, cameraPos.z);
            }
        }
    }
}
