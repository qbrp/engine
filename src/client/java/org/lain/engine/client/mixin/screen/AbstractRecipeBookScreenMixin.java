package org.lain.engine.client.mixin.screen;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractRecipeBookScreen.class)
class AbstractRecipeBookScreenMixin {
    @Shadow
    @Final
    private RecipeBookComponent<?> recipeBookComponent;

    @Inject(
            method = "initButton",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private void engine$removeButtons(CallbackInfo ci) {
        ci.cancel();
    }
}
