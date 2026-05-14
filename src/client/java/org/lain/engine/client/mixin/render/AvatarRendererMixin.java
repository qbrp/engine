package org.lain.engine.client.mixin.render;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL")
    )
    public void engine$updateRenderState(Avatar avatar, AvatarRenderState avatarRenderState, float f, CallbackInfo ci) {
        ClientMixinAccess.INSTANCE.updatePlayerRenderState(
                avatar,
                avatarRenderState,
                ((LivingEntityRenderer<Avatar, AvatarRenderState, PlayerModel>)(Object)this).getModel()
        );
    }
}
