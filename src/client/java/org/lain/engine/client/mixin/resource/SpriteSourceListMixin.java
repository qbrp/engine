package org.lain.engine.client.mixin.resource;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lain.engine.client.mc.ClientMixin;
import org.lain.engine.client.resources.EngineAtlasSource;
import org.lain.engine.client.resources.EngineTexture;
import org.lain.engine.client.resources.ResourceList;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(SpriteSourceList.class)
public class SpriteSourceListMixin {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Inject(method = "load", at = @At("RETURN"), cancellable = true)
    private static void engine$addCustomAtlas(
            ResourceManager resourceManager,
            ResourceLocation atlasId,
            CallbackInfoReturnable<SpriteSourceList> cir
    ) {
        ResourceList resourceList = ClientMixin.INSTANCE.getResourceList();
        if (resourceList != null) {
            List<EngineTexture> atlasTextures = resourceList.getTextureAssets().get(atlasId.getPath());
            if (atlasTextures == null || atlasTextures.isEmpty()) {
                return;
            }

            List<SpriteSource> sources = new ArrayList<>(
                    ((SpriteSourceListAccessor) (Object) cir.getReturnValue()).engine$getSources()
            );
            sources.add(new EngineAtlasSource(atlasTextures));
            cir.setReturnValue(SpriteSourceListAccessor.newAtlasLoader(sources));
            LOGGER.info("Atlas {} extended with {} Engine textures", atlasId, atlasTextures.size());
        }
    }
}
