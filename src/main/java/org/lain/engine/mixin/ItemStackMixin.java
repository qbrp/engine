package org.lain.engine.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.ClickType;
import org.lain.engine.item.EngineItem;
import org.lain.engine.mc.EngineItemReferenceComponent;
import org.lain.engine.mc.ItemsKt;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(
            method = "onStackClicked",
            at = @At(
                    value = "TAIL"
            ),
            cancellable = true
    )
    public void engine$onClicked(Slot slot, ClickType clickType, PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        ItemStack slotStack = slot.getStack();
        EngineItem slotItem = getEngineItem(slotStack);
        EngineItem item = getEngineItem((ItemStack) (Object)this);
        if (slotItem != null && item != null && !player.getEntityWorld().isClient()) {
            cir.setReturnValue(ServerMixinAccess.INSTANCE.onSlotEngineItemClicked(item, slotItem, slotStack, (ItemStack) (Object)this, player, clickType));
        }
    }

    @Unique
    private static EngineItem getEngineItem(ItemStack itemStack) {
        EngineItemReferenceComponent component = itemStack.get(ItemsKt.getENGINE_ITEM_REFERENCE_COMPONENT());
        if (component == null) {
            return null;
        }
        return component.getItem();
    }
}
