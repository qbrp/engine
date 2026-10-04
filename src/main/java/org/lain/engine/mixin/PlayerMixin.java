package org.lain.engine.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.lain.engine.mc.PlayerEntityAccess;
import org.lain.engine.mc.PlayerEntityAccessHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin implements PlayerEntityAccessHolder {
    @Unique
    private volatile PlayerEntityAccess engine$playerEntityAccess;

    @Override
    public PlayerEntityAccess engine$getPlayerEntityAccess() {
        PlayerEntityAccess access = engine$playerEntityAccess;
        if (access == null) {
            synchronized (this) {
                access = engine$playerEntityAccess;
                if (access == null) {
                    access = new PlayerEntityAccess();
                    engine$playerEntityAccess = access;
                }
            }
        }
        return access;
    }

    @Inject(
            method = "getDisplayName",
            at = @At(value = "RETURN"),
            cancellable = true
    )
    private void replaceName(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(engine$getPlayerEntityAccess().getDisplayName(cir.getReturnValue()));
    }

    @Inject(
            method = "getFlyingSpeed",
            at = @At("RETURN"),
            cancellable = true
    )
    public void engine$modifyFlyingSpeed(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValue() * engine$getPlayerEntityAccess().getFlyingSpeed());
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void engine$modifyNoPhysicsAiStep(CallbackInfo ci)
    {
        Player self = (Player)(Object)this;
        Boolean noPhysics = engine$getPlayerEntityAccess().getNoPhysics();

        if (noPhysics != null) {
            self.noPhysics = noPhysics;
        }
    }

    @ModifyExpressionValue(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z",
                    ordinal = 0
            )
    )
    private boolean engine$modifyNoPhysics(boolean original) {
        Boolean override = engine$getPlayerEntityAccess().getNoPhysics();
        return override != null ? override : original;
    }
}
