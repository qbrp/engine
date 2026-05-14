package org.lain.engine.client.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {
    @Inject(
            method = "setCarried",
            at = @At("TAIL")
    )
    public void engine$setCursorStack(ItemStack itemStack, CallbackInfo ci) {
        ClientMixinAccess.INSTANCE.onCursorStackSet(itemStack);
    }
}
