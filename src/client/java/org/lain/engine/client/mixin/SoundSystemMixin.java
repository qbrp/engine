package org.lain.engine.client.mixin;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {
    @Inject(
            method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F",
            at = @At("RETURN"),
            cancellable = true
    )
    public void engine$editVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        Float editedVolume = ClientMixinAccess.INSTANCE.editVolume(sound, cir.getReturnValue(), sound.getCategory());
        if (editedVolume != null) {
            cir.setReturnValue(editedVolume);
        }
    }
}
