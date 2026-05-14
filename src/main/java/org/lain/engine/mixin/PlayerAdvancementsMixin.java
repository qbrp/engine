package org.lain.engine.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsMixin {
    @Inject(
            method = "award",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"
            ),
            cancellable = true
    )
    public void engine$disableAdvancementsMessages(AdvancementHolder advancementHolder, String string, CallbackInfoReturnable<Boolean> cir) {
        if (ServerMixinAccess.INSTANCE.isAchievementMessagesDisabled()) {
            cir.cancel();
        }
    }
}
