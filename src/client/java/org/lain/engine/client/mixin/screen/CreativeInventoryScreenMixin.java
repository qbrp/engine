package org.lain.engine.client.mixin.screen;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.lain.engine.client.mixin.CreativeModeTabAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

@Mixin(CreativeModeInventoryScreen.class)
public class CreativeInventoryScreenMixin {
    @Redirect(
            method = "getTooltipFromContainerItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/CreativeModeTabs;tabs()Ljava/util/List;"
            )
    )
    public List<CreativeModeTabAccessor> engine$hideItemGroupTooltip() {
        return new ArrayList<>();
    }
}
