package org.lain.engine.client.mixin.screen;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractFurnaceScreen.class)
class AbstractFurnaceScreenMixin {
    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractFurnaceScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"
            )
    )
    private GuiEventListener engine$skipRecipeButton(AbstractFurnaceScreen<?> screen, GuiEventListener widget) {
        return widget;
    }
}
