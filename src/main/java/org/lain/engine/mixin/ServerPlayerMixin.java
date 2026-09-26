package org.lain.engine.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Shadow
    @Final
    public ServerPlayerGameMode gameMode;

    @Redirect(
            method = "loadGameTypes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;calculateGameModeForNewPlayer(Lnet/minecraft/world/level/GameType;)Lnet/minecraft/world/level/GameType;"
            )
    )
    private GameType engine$cancelLoadGameTypes(ServerPlayer instance, GameType gameType) {
        return GameType.SPECTATOR;
    }
}
