package org.lain.engine.client.mixin;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClickEvent.Action.class)
public class ClickEventActionMixin {

    // Потенциальная дыра в безопасности, лучше убрать в будущем
    @Inject(
            method = "filterForSerialization",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void alwaysAllow(
            ClickEvent.Action action,
            CallbackInfoReturnable<DataResult<ClickEvent.Action>> cir
    ) {
        cir.setReturnValue(DataResult.success(action, Lifecycle.stable()));
    }
}

