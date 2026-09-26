package org.lain.engine.client.mixin;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.lain.engine.client.mc.ClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlayerInfo.class, priority = 10_000)
public class PlayerInfoMixin {
    @Inject(
            method = "getSkin",
            at = @At("HEAD"),
            cancellable = true
    )
    private void engine$getSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin enginePlayerSkin = ClientMixin.INSTANCE.getPlayerSkinThreadSafe((PlayerInfo)(Object)this);
        if (enginePlayerSkin != null) {
            cir.setReturnValue(enginePlayerSkin);
        }
    }
}
