package org.lain.engine.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    @Redirect(
            method = "onPlayerConnect",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V"
            )
    )
    private void engine$redirectBroadcastOnJoin(PlayerManager instance, Text message, boolean overlay) {
        if (!ServerMixinAccess.INSTANCE.shouldCancelSendLeaveMessage()) {
            instance.broadcast(message, overlay);
        }
    }
}