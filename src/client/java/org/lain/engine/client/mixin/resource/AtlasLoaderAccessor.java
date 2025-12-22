package org.lain.engine.client.mixin.resource;

import net.minecraft.client.texture.atlas.AtlasLoader;
import net.minecraft.client.texture.atlas.AtlasSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(AtlasLoader.class)
public interface AtlasLoaderAccessor {
    @Invoker("<init>")
    static AtlasLoader newAtlasLoader(List<AtlasSource> sources) {
        throw new AssertionError();
    }
}
