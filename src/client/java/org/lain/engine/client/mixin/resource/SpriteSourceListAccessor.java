package org.lain.engine.client.mixin.resource;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(SpriteSourceList.class)
public interface SpriteSourceListAccessor {
    @Invoker("<init>")
    static SpriteSourceList newAtlasLoader(List<SpriteSource> sources) {
        throw new AssertionError();
    }
}
