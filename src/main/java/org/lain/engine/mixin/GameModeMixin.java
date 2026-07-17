package org.lain.engine.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public class GameModeMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(
            method = "changeGameModeForPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getAbilities()Lnet/minecraft/world/entity/player/Abilities;"),
            cancellable = true
    )
    public void engine$changeGameMode(GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        if (!ServerMixinAccess.INSTANCE.allowedToPlay(player)) {
            ServerMixinAccess.INSTANCE.sendForbiddenToPlayNotification(player);
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
