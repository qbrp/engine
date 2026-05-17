package org.lain.engine.client.mixin.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.resources.EngineAtlasSource;
import org.lain.engine.client.resources.EngineTexture;
import org.lain.engine.client.resources.ResourceList;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

@Mixin(SpriteSourceList.class)
public class SpriteSourceListMixin {
    @Shadow @Final private static FileToIdConverter ATLAS_INFO_CONVERTER;

    @Shadow @Final private static Logger LOGGER;

    @Inject(
            method = "load",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;addAll(Ljava/util/Collection;)Z",
                    shift = At.Shift.AFTER
            )
    )
    private static void addCustomAtlas(
            ResourceManager resourceManager,
            Identifier identifier,
            CallbackInfoReturnable<SpriteSourceList> cir,
            @Local List<SpriteSource> list
    ) {
        ResourceList resourceList = ClientMixinAccess.INSTANCE.getResourceList();
        List<EngineTexture> atlasTextures = resourceList.getTextureAssets().get(
                identifier.getPath().replace("atlases/", "").replace(".json", "")
        );

        if (atlasTextures != null) {
            list.add(new EngineAtlasSource(atlasTextures));
            LOGGER.info("Атлас {} дополнен {} текстурами Engine", identifier, atlasTextures.size());
        }
    }
}
