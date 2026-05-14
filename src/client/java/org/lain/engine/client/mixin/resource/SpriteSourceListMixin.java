package org.lain.engine.client.mixin.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

@Mixin(SpriteSourceList.class)
public class SpriteSourceListMixin {
    @Shadow @Final private static FileToIdConverter ATLAS_INFO_CONVERTER;

    @Shadow @Final private static Logger LOGGER;

    /**
     * @author lain1wakura
     * @reason Через другие миксины неудобно
     */
    @Overwrite
    public static SpriteSourceList load(ResourceManager resourceManager, Identifier id) {
        Identifier identifier = ATLAS_INFO_CONVERTER.idToFile(id);
        ArrayList<SpriteSource> list = new ArrayList<SpriteSource>();
        ResourceList resourceList = ClientMixinAccess.INSTANCE.getResourceList();
        List<EngineTexture> atlasTextures = resourceList.getTextureAssets().get(
                id.getPath().replace("atlases/", "").replace(".json", "")
        );

        if (atlasTextures != null) {
            list.add(new EngineAtlasSource(atlasTextures));
            LOGGER.info("Атлас {} дополнен {} текстурами Engine", id, atlasTextures.size());
        }

        for (Resource resource : resourceManager.getResourceStack(identifier)) {
            try {
                BufferedReader bufferedReader = resource.openAsReader();
                try {
                    Dynamic<JsonElement> dynamic = new Dynamic<JsonElement>(JsonOps.INSTANCE, JsonParser.parseReader(bufferedReader));
                    list.addAll(SpriteSources.FILE_CODEC.parse(dynamic).getOrThrow());
                } finally {
                    if (bufferedReader == null) continue;
                    bufferedReader.close();
                }
            } catch (Exception exception) {
                LOGGER.error("Failed to parse atlas definition {} in pack {}", identifier, resource.sourcePackId(), exception);
            }
        }
        return SpriteSourceListAccessor.newAtlasLoader(list);
    }
}
