package org.lain.engine.client.mixin;

import net.minecraft.component.ComponentType;
import net.minecraft.component.MergedComponentMap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lain.engine.mc.EngineItemReferenceComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Shadow @Final
    MergedComponentMap components;

    @Shadow public abstract Text getFormattedName();

    @Inject(
            method = "getTooltip",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    public void engine$appendTooltip(Item.TooltipContext context, @Nullable PlayerEntity player, TooltipType type, CallbackInfoReturnable<List<Text>> cir) {
        if (this.components.contains(EngineItemReferenceComponent.Companion.getTYPE())) {
            ArrayList<Text> overridenText = new ArrayList<>();
            overridenText.add(getFormattedName());
            cir.setReturnValue(overridenText);
            cir.cancel();
        }
    }
}
