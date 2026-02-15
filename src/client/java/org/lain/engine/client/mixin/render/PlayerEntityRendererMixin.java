package org.lain.engine.client.mixin.render;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.item.EngineItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {
    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL")
    )
    public void engine$updateRenderState(PlayerLikeEntity playerLikeEntity, PlayerEntityRenderState playerEntityRenderState, float f, CallbackInfo ci) {
        ClientMixinAccess.INSTANCE.updatePlayerRenderState(playerLikeEntity, playerEntityRenderState, f);
    }
}
