package org.lain.engine.client.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundSystemMixin {
    @Inject(
            method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"),
            cancellable = true
    )
    public void engine$editVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        Float editedVolume = ClientMixinAccess.INSTANCE.editVolume(sound, cir.getReturnValue(), sound.getSource());
        if (editedVolume != null) {
            cir.setReturnValue(editedVolume);
        }
    }
}
