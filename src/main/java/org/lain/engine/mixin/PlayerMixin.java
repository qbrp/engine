package org.lain.engine.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(
            method = "getDisplayName",
            at = @At(value = "RETURN"),
            cancellable = true
    )
    private void replaceName(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(ServerMixinAccess.INSTANCE.getDisplayName((Player) ((Object)this)));
    }
}