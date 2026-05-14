package org.lain.engine.client.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.lain.engine.CommonEngineServerMod;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.lain.engine.client.mc.sound.UtilKt.loadEngineStaticSound;

@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {
    @Shadow @Final private Map<Identifier, CompletableFuture<SoundBuffer>> cache;

    @Inject(
            method = "getCompleteBuffer",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$loadStatic(Identifier id, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
        if (Objects.equals(id.getNamespace(), CommonEngineServerMod.MOD_ID) && !id.getPath().startsWith("sounds/builtin")) {
            cir.setReturnValue(
                loadEngineStaticSound(
                    ClientMixinAccess.INSTANCE.getAssets(),
                    this.cache,
                    Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replaceFirst("sounds/", ""))
                )
            );
            cir.cancel();
        }
    }
}
