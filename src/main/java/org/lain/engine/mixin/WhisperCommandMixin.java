package org.lain.engine.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.MsgCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MsgCommand.class)
public class WhisperCommandMixin {
    @Inject(
            method = "register",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CallbackInfo ci) {
        ci.cancel();
    }
}

