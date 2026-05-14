package org.lain.engine.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public class PlayerListMixin {
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