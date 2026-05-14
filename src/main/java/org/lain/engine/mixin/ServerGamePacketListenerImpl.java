package org.lain.engine.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(net.minecraft.server.network.ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImpl {
    @Shadow
    public ServerPlayer player;

    @Redirect(
            method = "removePlayerFromWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
            )
    )
    private void engine$sendDisconnectedMessage(PlayerList instance, Component message, boolean overlay) {
        if (!ServerMixinAccess.INSTANCE.shouldCancelSendJoinMessage()) {
            instance.broadcastSystemMessage(message, overlay);
        }
    }
}
