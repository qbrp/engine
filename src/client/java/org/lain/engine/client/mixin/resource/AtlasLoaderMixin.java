package org.lain.engine.client.mixin.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.texture.atlas.AtlasLoader;
import net.minecraft.client.texture.atlas.AtlasSource;
import net.minecraft.client.texture.atlas.AtlasSourceManager;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.mc.EngineAtlasSource;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

@Mixin(AtlasLoader.class)
public class AtlasLoaderMixin {
    @Shadow @Final private static ResourceFinder FINDER;

    @Shadow @Final private static Logger LOGGER;

    /**
     * @author lain1wakura
     * @reason Через другие миксины неудобно
     */
    @Overwrite
    public static AtlasLoader of(ResourceManager resourceManager, Identifier id) {
        Identifier identifier = FINDER.toResourcePath(id);
        ArrayList<AtlasSource> list = new ArrayList<AtlasSource>();
        if (Objects.equals(identifier, Identifier.of("engine", "engine"))) {
            list.add(new EngineAtlasSource(ClientMixinAccess.INSTANCE.getResourceList()));
        }
        for (Resource resource : resourceManager.getAllResources(identifier)) {
            try {
                BufferedReader bufferedReader = resource.getReader();
                try {
                    Dynamic<JsonElement> dynamic = new Dynamic<JsonElement>(JsonOps.INSTANCE, JsonParser.parseReader(bufferedReader));
                    list.addAll(AtlasSourceManager.LIST_CODEC.parse(dynamic).getOrThrow());
                } finally {
                    if (bufferedReader == null) continue;
                    bufferedReader.close();
                }
            } catch (Exception exception) {
                LOGGER.error("Failed to parse atlas definition {} in pack {}", identifier, resource.getPackId(), exception);
            }
        }
        return AtlasLoaderAccessor.newAtlasLoader(list);
    }
}
