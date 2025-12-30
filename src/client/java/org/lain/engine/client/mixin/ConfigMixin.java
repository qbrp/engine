package org.lain.engine.client.mixin;

import me.fzzyhmstrs.fzzy_config.config.Config;
import org.lain.engine.client.mc.EngineFzzyConfig;
import org.lain.engine.client.util.EngineOptionsKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

@Mixin(Config.class)
public class ConfigMixin {
    @Inject(
            method = "getDir",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    public void engine$getDir(CallbackInfoReturnable<File> cir) {
        if (((Object)(this)) instanceof EngineFzzyConfig) {
            cir.setReturnValue(EngineOptionsKt.getCONFIG_DIR().getParentFile());
        }
    }
}
