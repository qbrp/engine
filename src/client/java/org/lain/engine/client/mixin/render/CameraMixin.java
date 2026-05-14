package org.lain.engine.client.mixin.render;

import kotlin.Unit;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.swing.text.html.BlockView;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void move(float f, float g, float h);

    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Shadow private float xRot;

    @Shadow private float yRot;

    @Inject(
            method = "setup",
            at = @At("TAIL")
    )
    public void engine$update(Level level, Entity entity, boolean bl, boolean bl2, float tickProgress, CallbackInfo ci) {
        org.lain.engine.client.render.Camera camera = ClientMixinAccess.INSTANCE.getCamera();
        camera.update(
                (vec) -> {
                    move(-vec.getX(), vec.getY(), vec.getZ());
                    return Unit.INSTANCE;
                },
                (vec) -> {
                    setRotation(this.yRot + vec.getX(), this.xRot + vec.getY());
                    return Unit.INSTANCE;
                },
                tickProgress
        );
    }
}
