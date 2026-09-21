package org.lain.engine.client.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
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
    @Inject(method = "render", at = @At("HEAD"))
    private void engine$updateRenderState(
            AbstractClientPlayer player,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int light,
            CallbackInfo ci
    ) {
        PlayerRenderer renderer = (PlayerRenderer) (Object) this;
        ClientMixin.INSTANCE.updatePlayerRenderState(player, renderer.getModel());
    }
}
