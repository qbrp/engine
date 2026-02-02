package org.lain.engine.client.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerSyncHandler;
import org.jetbrains.annotations.Nullable;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.item.EngineItem;
import org.lain.engine.mc.ItemsKt;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public class ScreenHandlerMixin {
    @Inject(
            method = "setCursorStack",
            at = @At("TAIL")
    )
    public void engine$setCursorStack(ItemStack stack, CallbackInfo ci) {
        ClientMixinAccess.INSTANCE.onCursorStackSet(stack);
    }
}
