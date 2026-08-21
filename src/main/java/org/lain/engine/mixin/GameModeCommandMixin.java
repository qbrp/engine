package org.lain.engine.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.lain.engine.mc.ServerMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameModeCommand.class)
public class GameModeCommandMixin {
    @Inject(
            method = "logGamemodeChange",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private static void engine$notifyGameModeChange(CommandSourceStack commandSourceStack, ServerPlayer serverPlayer, GameType gameType, CallbackInfo ci) {
        if (commandSourceStack.getEntity() == serverPlayer) {
            ServerMixin.INSTANCE.notifyPlayerGameModeChange(serverPlayer, gameType);
            ci.cancel();
        }
    }
}
