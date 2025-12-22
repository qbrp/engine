package org.lain.engine.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    @Inject(
            method = "getPlayerListName",
            at = @At(value = "RETURN"),
            cancellable = true
    )
    public void engine$getPlayerListName(CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(ServerMixinAccess.INSTANCE.getDisplayName((PlayerEntity)((Object) this)));
    }
}
