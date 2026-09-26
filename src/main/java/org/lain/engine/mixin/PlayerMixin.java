package org.lain.engine.mixin;

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
    private void engine$modifyNoPhysics(CallbackInfo ci)
    {
        Player self = (Player)(Object)this;
        Boolean noPhysics = engine$getPlayerEntityAccess().getNoPhysics();

        if (noPhysics != null) {
            self.noPhysics = noPhysics;
        }
    }
}
