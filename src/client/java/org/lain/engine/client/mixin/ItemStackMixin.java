package org.lain.engine.client.mixin;

import com.google.common.collect.Lists;
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
import org.lain.engine.client.mc.MinecraftUtilKt;
import org.lain.engine.item.EngineItem;
import org.lain.engine.item.TooltipKt;
import org.lain.engine.mc.EngineItemReferenceComponent;
import org.lain.engine.mc.ItemsKt;
import org.lain.engine.mc.ServerMixinAccess;
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

    @Shadow public abstract Text getFormattedName();

    @Inject(
            method = "getTooltip",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    public void engine$getTooltip(Item.TooltipContext context, @Nullable PlayerEntity player, TooltipType type, CallbackInfoReturnable<List<Text>> cir) {
        if (components.contains(ItemsKt.getENGINE_ITEM_INSTANTIATE_COMPONENT())) {
            ArrayList<Text> list = Lists.newArrayList();
            list.add(getFormattedName());
            cir.setReturnValue(list);
            cir.cancel();
        }
    }

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
            for (String line : TooltipKt.getTooltip(engineItem, type.isAdvanced())) {
                textConsumer.accept(MinecraftUtilKt.parseMiniMessageClient(line));
            }
            ci.cancel();
        }
    }

    @Inject(
            method = "onStackClicked",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true)
    public void engine$onClicked(Slot slot, ClickType clickType, PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (!player.getEntityWorld().isClient()) return;
        ItemStack slotStack = slot.getStack();
        EngineItem slotItem = getEngineItem(slotStack);
        EngineItem item = getEngineItem((ItemStack) (Object)this);
        if (slotItem != null && item != null) {
            cir.setReturnValue(ServerMixinAccess.INSTANCE.onSlotEngineItemClicked(item, slotItem, slotStack, (ItemStack) (Object)this, player));
        }
    }

    @Unique
    private static EngineItem getEngineItem(ItemStack itemStack) {
        EngineItemReferenceComponent component = itemStack.get(ItemsKt.getENGINE_ITEM_REFERENCE_COMPONENT());
        if (component == null) {
            return null;
        }
        return ClientItemStorageKt.getClientItem(component);
    }
}
