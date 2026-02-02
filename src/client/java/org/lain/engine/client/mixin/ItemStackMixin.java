package org.lain.engine.client.mixin;

import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.MergedComponentMap;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipAppender;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import org.jetbrains.annotations.Nullable;
import org.lain.engine.client.ClientItemStorageKt;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.item.EngineItem;
import org.lain.engine.item.TooltipKt;
import org.lain.engine.mc.EngineItemReferenceComponent;
import org.lain.engine.util.text.TextAdaptersKt;
import org.lain.engine.util.text.TextKt;
import org.lain.engine.util.text.TextSerializationKt;
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
import java.util.Objects;
import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Shadow @Final
    MergedComponentMap components;

    @Shadow public abstract <T extends TooltipAppender> void appendComponentTooltip(ComponentType<T> componentType, Item.TooltipContext context, TooltipDisplayComponent displayComponent, Consumer<Text> textConsumer, TooltipType type);

    @Shadow public abstract Item getItem();

    @Shadow @Final @Deprecated private @Nullable Item item;

    @Inject(
            method = "appendTooltip",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true)
    public void engine$appendTooltip(
            Item.TooltipContext context,
            TooltipDisplayComponent displayComponent,
            @Nullable PlayerEntity player,
            TooltipType type,
            Consumer<Text> textConsumer,
            CallbackInfo ci
    ) {
        EngineItem engineItem = getEngineItem((ItemStack)((Object)this));
        if (engineItem != null) {
            appendComponentTooltip(DataComponentTypes.LORE, context, displayComponent, textConsumer, type);
            for (String line : TooltipKt.getTooltip(engineItem)) {
                textConsumer.accept(TextSerializationKt.parseMiniMessage(line));
            }
            ci.cancel();
        }
    }

    @Inject(
            method = "onClicked",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    public void engine$onClicked(ItemStack stack, Slot slot, ClickType clickType, PlayerEntity player, StackReference cursorStackReference, CallbackInfoReturnable<Boolean> cir) {
        ItemStack slotStack = slot.getStack();
        EngineItem slotItem = getEngineItem(slotStack);
        EngineItem item = getEngineItem(stack);
        if (slotItem != null && item != null) {
            cir.setReturnValue(ClientMixinAccess.INSTANCE.onSlotEngineItemClicked(item, slotItem, cursorStackReference));
        }
    }

    @Unique
    private static boolean isEngineItem(ItemStack itemStack) {
        return itemStack.getComponents().contains(EngineItemReferenceComponent.Companion.getTYPE());
    }

    @Unique
    private static EngineItem getEngineItem(ItemStack itemStack) {
        EngineItemReferenceComponent component = itemStack.get(EngineItemReferenceComponent.Companion.getTYPE());
        if (component == null) {
            return null;
        }
        return ClientItemStorageKt.getClientItem(component);
    }
}
