package org.lain.engine.client.mixin.screen;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InventoryScreen.class)
class InventoryRecipeBookScreenMixin {
    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"
            )
    )
    private GuiEventListener engine$skipRecipeButton(InventoryScreen screen, GuiEventListener widget) {
        return widget;
    }

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;addWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"
            )
    )
    private GuiEventListener engine$skipRecipeBook(InventoryScreen screen, GuiEventListener widget) {
        return widget;
    }
}
