package org.lain.engine.client.mixin.screen;

import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.ItemGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

@Mixin(CreativeInventoryScreen.class)
public class CreativeInventoryScreenMixin {
    @Redirect(
            method = "getTooltipFromItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemGroups;getGroupsToDisplay()Ljava/util/List;"
            )
    )
    public List<ItemGroup> engine$hideItemGroupTooltip() {
        return new ArrayList<>();
    }
}
