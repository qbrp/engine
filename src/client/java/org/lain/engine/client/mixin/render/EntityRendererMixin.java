package org.lain.engine.client.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.ItemFrameEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
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
        if ((((Object)this) instanceof ItemFrameEntityRenderer<?>) && state != null) {
            ItemFrameEntityRenderState renderState = (ItemFrameEntityRenderState)state;
            Boolean culling = PropertiesKt.getCulling(renderState.itemRenderState);
            if (culling != null && !culling) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    @Redirect(
            method = "getAndUpdateRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/EntityRenderer;updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V"
            )
    )
    public void engine$storeRenderModel(EntityRenderer instance, Entity entity, EntityRenderState state, float tickProgress) {
        instance.updateRenderState(entity, state, tickProgress);
        this.state = state;
    }

    @WrapOperation(
            method = "getLight",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/EntityRenderer;getBlockLight(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/BlockPos;)I"
            )
    )
    private int illuminated$onForceEntityLitUp(EntityRenderer<?, ?> instance, Entity entity, BlockPos pos, Operation<Integer> original) {
//        if (Illuminated.isHoldingPoweredFlashlight(entity)) {
//            return 15;
//        }

        return original.call(instance, entity, pos);
    }
}
