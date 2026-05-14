package org.lain.engine.client.mixin;

import com.google.common.collect.Lists;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import org.jetbrains.annotations.Nullable;
import org.lain.cyberia.ecs.ComponentType;
import org.lain.engine.client.ClientItemStorageKt;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.mc.UtilKt;
import org.lain.engine.item.TooltipKt;
import org.lain.engine.mc.EngineItemReferenceComponent;
import org.lain.engine.mc.ItemsKt;
import org.lain.engine.mc.ServerMixinAccess;
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
    @Shadow @Final
    PatchedDataComponentMap components;

    @Shadow public abstract Item getItem();

    @Shadow @Final @Deprecated private @Nullable Item item;

    @Shadow
    public abstract <T extends TooltipProvider> void addToTooltip(DataComponentType<T> dataComponentType, Item.TooltipContext tooltipContext, TooltipDisplay tooltipDisplay, Consumer<Component> consumer, TooltipFlag tooltipFlag);

    @Shadow
    public abstract @org.jspecify.annotations.Nullable Component getCustomName();

    @Shadow
    public abstract Component getStyledHoverName();

    @Inject(
            method = "getTooltipLines",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    public void engine$getTooltip(Item.TooltipContext tooltipContext, @org.jspecify.annotations.Nullable Player player, TooltipFlag tooltipFlag, CallbackInfoReturnable<List<Component>> cir) {
        if (components.has(ItemsKt.getENGINE_ITEM_INSTANTIATE_COMPONENT())) {
            ArrayList<Component> list = Lists.newArrayList();
            list.add(getStyledHoverName());
            cir.setReturnValue(list);
            cir.cancel();
        }
    }

    @Inject(
            method = "addDetailsToTooltip",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true)
    public void engine$appendTooltip(
            Item.TooltipContext context,
            TooltipDisplay displayComponent,
            @org.jspecify.annotations.Nullable Player player,
            TooltipFlag type,
            Consumer<Component> textConsumer,
            CallbackInfo ci
    ) {
        Integer engineItem = getEngineItem((ItemStack)((Object)this));
        if (engineItem != null) {
            addToTooltip(DataComponents.LORE, context, displayComponent, textConsumer, type);
            for (String line : ClientMixinAccess.INSTANCE.getTooltip(engineItem, type.isAdvanced())) {
                textConsumer.accept(UtilKt.parseMiniMessageClient(line));
            }
        }
        if (engineItem != null || components.has(ItemsKt.getENGINE_ITEM_INSTANTIATE_COMPONENT())) {
            ci.cancel();
        }
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
        Integer item = getEngineItem((ItemStack) (Object)this);
        if (slotItem != null && item != null) {
            cir.setReturnValue(ServerMixinAccess.INSTANCE.onSlotEngineItemClicked(item, slotItem, slotStack, (ItemStack) (Object)this, player, clickAction));
        }
    }

    @Unique
    private static Integer getEngineItem(ItemStack itemStack) {
        EngineItemReferenceComponent component = itemStack.get(ItemsKt.getENGINE_ITEM_REFERENCE_COMPONENT());
        if (component == null) {
            return null;
        }
        return ClientItemStorageKt.getClientItem(component);
    }
}
