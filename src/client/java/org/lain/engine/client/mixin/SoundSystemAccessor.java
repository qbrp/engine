package org.lain.engine.client.mixin;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(SoundSystem.class)
public interface SoundSystemAccessor {
    @Accessor("sources")
    Map<SoundInstance, Channel.SourceManager> engine$getSources();

    @Invoker("getAdjustedVolume")
    float engine$getAdjustedVolume(SoundInstance sound);
}
