package org.lain.engine.mixin;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancementTracker.class)
public class PlayerAdvancementTrackerMixin {
    @Inject(
            method = "grantCriterion",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"
            ),
            cancellable = true
    )
    public void engine$disableAdvancementsMessages(AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (ServerMixinAccess.INSTANCE.isAchievementMessagesDisabled()) {
            cir.cancel();
        }
    }
}
