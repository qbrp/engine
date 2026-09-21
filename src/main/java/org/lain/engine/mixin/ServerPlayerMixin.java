package org.lain.engine.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Inject(
            method = "loadGameTypes",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    private void engine$cancelLoadGameTypes(CompoundTag compoundTag, CallbackInfo ci) {
        ci.cancel();
    }
}
