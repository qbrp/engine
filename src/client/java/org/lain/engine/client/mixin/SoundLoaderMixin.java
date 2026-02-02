package org.lain.engine.client.mixin;

import net.minecraft.client.sound.SoundLoader;
import net.minecraft.client.sound.StaticSound;
import net.minecraft.util.Identifier;
import org.lain.engine.CommonEngineServerMod;
import org.lain.engine.client.mc.MinecraftAudioKt;
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

@Mixin(SoundLoader.class)
public class SoundLoaderMixin {
    @Shadow @Final private Map<Identifier, CompletableFuture<StaticSound>> loadedSounds;

    @Inject(
            method = "loadStatic(Lnet/minecraft/util/Identifier;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$loadStatic(Identifier id, CallbackInfoReturnable<CompletableFuture<StaticSound>> cir) {
        if (Objects.equals(id.getNamespace(), CommonEngineServerMod.MOD_ID)) {
            cir.setReturnValue(
                MinecraftAudioKt.loadEngineStaticSound(
                    ClientMixinAccess.INSTANCE.getAssets(),
                    this.loadedSounds,
                    Identifier.of(id.getNamespace(), id.getPath().replaceFirst("sounds/", ""))
                )
            );
            cir.cancel();
        }
    }
}
