package org.lain.engine.client.mixin.resource;

import com.google.gson.Gson;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(JsonUnbakedModel.class)
public interface JsonUnbakedModelAccessor {
    @Accessor("GSON")
    static Gson engine$getGson() {
        throw new RuntimeException();
    }
}
