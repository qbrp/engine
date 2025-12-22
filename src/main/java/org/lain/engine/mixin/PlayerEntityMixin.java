package org.lain.engine.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(
            method = "getDisplayName",
            at = @At(value = "RETURN"),
            cancellable = true
    )
    private void replaceName(CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(ServerMixinAccess.INSTANCE.getDisplayName((PlayerEntity)((Object)this)));
    }
}