package org.lain.engine.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(
            method = "placeNewPlayer",
            at = @At("HEAD")
    )
    private void engine$forceSpectatorOnJoin(Connection connection, ServerPlayer serverPlayer, CommonListenerCookie commonListenerCookie, CallbackInfo ci) {
        ((ServerPlayerGameModeAccessor) serverPlayer.gameMode).engine$setGameModeForPlayer(GameType.SPECTATOR, null);
    }

    @Redirect(
            method = "placeNewPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
            )
    )
    private void engine$redirectBroadcastOnJoin(PlayerList instance, Component message, boolean overlay) {
        if (!ServerMixinAccess.INSTANCE.shouldCancelSendLeaveMessage()) {
            instance.broadcastSystemMessage(message, overlay);
        }
    }
}
