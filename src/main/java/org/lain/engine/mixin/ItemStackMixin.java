package org.lain.engine.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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
            method = "overrideStackedOnOther",
            at = @At(
                    value = "TAIL"
            ),
            cancellable = true
    )
    public void engine$onClicked(Slot slot, ClickAction clickAction, Player player, CallbackInfoReturnable<Boolean> cir) {
        ItemStack slotStack = slot.getItem();
        Integer slotItem = getEngineItem(slotStack);
        Integer item = getEngineItem((ItemStack) (Object)this);
        if (slotItem != null && item != null && !player.level().isClientSide()) {
            cir.setReturnValue(ServerMixinAccess.INSTANCE.onSlotEngineItemClicked(item, slotItem, slotStack, (ItemStack) (Object)this, player, clickAction));
        }
    }

    @Unique
    private static Integer getEngineItem(ItemStack itemStack) {
        EngineItemReferenceComponent component = itemStack.get(ItemsKt.getENGINE_ITEM_REFERENCE_COMPONENT());
        if (component == null) {
            return null;
        }
        return component.getItem();
    }
}
