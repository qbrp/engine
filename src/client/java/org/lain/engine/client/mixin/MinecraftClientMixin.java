package org.lain.engine.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow @Nullable public ClientPlayerEntity player;

    @Shadow protected abstract boolean doAttack();

    @Redirect(
            method = "handleInputEvents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MinecraftClient;doAttack()Z"
            )
    )
    public boolean engine$handleInputEvents(MinecraftClient instance) {
        ClientMixinAccess mixinAccess = ClientMixinAccess.INSTANCE;
        if (!mixinAccess.predictItemLeftClickInteraction()) {
            return this.doAttack();
        } else {
            mixinAccess.onLeftMouseClick();
            return false;
        }
    }
}
