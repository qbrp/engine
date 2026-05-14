package org.lain.engine.client.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.lain.engine.client.resources.PropertiesKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Unique
    private EntityRenderState state = null;

    @Inject(
            method = "shouldRender",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    public void engine$disableCulling(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if ((((Object)this) instanceof ItemFrameRenderer<?>) && state != null) {
            ItemFrameRenderState renderState = (ItemFrameRenderState)state;
            Boolean culling = PropertiesKt.getCulling(renderState.item);
            if (culling != null && !culling) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    @Redirect(
            method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V"
            )
    )
    public void engine$storeRenderModel(EntityRenderer instance, Entity entity, EntityRenderState state, float tickProgress) {
        instance.extractRenderState(entity, state, tickProgress);
        this.state = state;
    }

    @WrapOperation(
            method = "getPackedLightCoords",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getBlockLightLevel(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)I"
            )
    )
    private int illuminated$onForceEntityLitUp(EntityRenderer<?, ?> instance, Entity entity, BlockPos pos, Operation<Integer> original) {
//        if (Illuminated.isHoldingPoweredFlashlight(entity)) {
//            return 15;
//        }

        return original.call(instance, entity, pos);
    }
}
