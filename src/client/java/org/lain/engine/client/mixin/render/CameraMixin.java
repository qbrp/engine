package org.lain.engine.client.mixin.render;

import kotlin.Unit;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void moveBy(float f, float g, float h);

    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Shadow private float yaw;

    @Shadow private float pitch;

    @Inject(
            method = "update",
            at = @At("TAIL")
    )
    public void engine$update(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        org.lain.engine.client.render.Camera camera = ClientMixinAccess.INSTANCE.getCamera();
        camera.update(
                (vec) -> {
                    moveBy(-vec.getX(), vec.getY(), vec.getZ());
                    return Unit.INSTANCE;
                },
                (vec) -> {
                    setRotation(this.yaw + vec.getX(), this.pitch + vec.getY());
                    return Unit.INSTANCE;
                },
                tickProgress
        );
    }
}
