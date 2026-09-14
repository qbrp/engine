package org.lain.engine.client.mixin;

import com.google.common.collect.Lists;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import org.jetbrains.annotations.Nullable;
import org.lain.engine.client.mc.ClientMixin;
import org.lain.engine.client.mc.UtilKt;
import org.lain.engine.mc.InventoryActionsKt;
import org.lain.engine.mc.ecs.ItemStacksKt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Shadow
    @Final
    PatchedDataComponentMap components;

    @Shadow
    public abstract <T extends TooltipProvider> void addToTooltip(DataComponentType<T> dataComponentType, Item.TooltipContext tooltipContext, Consumer<Component> consumer, TooltipFlag tooltipFlag);

    @Shadow
    public abstract Component getHoverName();

    @Shadow
    public abstract Rarity getRarity();

    @Inject(
            method = "getTooltipLines",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    public void engine$getTooltip(Item.TooltipContext tooltipContext, @org.jspecify.annotations.Nullable Player player, TooltipFlag tooltipFlag, CallbackInfoReturnable<List<Component>> cir) {
        Integer engineItem = getEngineItem((ItemStack) ((Object) this));
        boolean instantiateItem = components.has(ItemStacksKt.getENGINE_ITEM_INSTANTIATE_COMPONENT());
        if (engineItem == null && !instantiateItem) {
            return;
        }

        ArrayList<Component> lines = Lists.newArrayList();
        MutableComponent name = Component.empty().append(getHoverName()).withStyle(getRarity().color());
        if (components.has(DataComponents.CUSTOM_NAME)) {
            name.withStyle(ChatFormatting.ITALIC);
        }
        lines.add(name);

        if (engineItem != null) {
            addToTooltip(DataComponents.LORE, tooltipContext, lines::add, tooltipFlag);
            for (String line : ClientMixin.INSTANCE.getTooltip(engineItem, tooltipFlag.isAdvanced())) {
                lines.add(UtilKt.parseMiniMessageClient(line));
            }
        }

        cir.setReturnValue(lines);
        cir.cancel();
    }

    @Inject(
            method = "overrideStackedOnOther",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true)
    public void engine$onClicked(Slot slot, ClickAction clickAction, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!player.level().isClientSide()) return;
        ItemStack slotStack = slot.getItem();
        Integer slotItem = getEngineItem(slotStack);
        Integer item = getEngineItem((ItemStack) (Object) this);
        if (slotItem != null && item != null) {
            cir.setReturnValue(InventoryActionsKt.onSlotEngineItemClicked(item, slotItem, slotStack, (ItemStack) (Object) this, player, clickAction));
        }
    }

    @Unique
    private static Integer getEngineItem(ItemStack itemStack) {
        return ClientMixin.INSTANCE.getEngineItem(itemStack);
    }
}
