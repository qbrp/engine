package org.lain.engine.client.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.lain.engine.client.mc.ClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class AvatarRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void engine$beforeRender(
            AbstractClientPlayer player,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int light,
            CallbackInfo ci
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        // Detached-camera mods can use a LocalPlayer as the camera. FirstPerson may try to
        // render that synthetic player at the camera origin when its compatibility ыhook is absent.
        if (player == minecraft.getCameraEntity() && player != minecraft.player) {
            ci.cancel();
            return;
        }

        if (player.isSpectator() && !ClientMixin.INSTANCE.renderSpectatorPlayers()) {
            ci.cancel();
        }
    }
}
