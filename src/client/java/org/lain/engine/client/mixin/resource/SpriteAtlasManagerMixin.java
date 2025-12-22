package org.lain.engine.client.mixin.resource;

import net.minecraft.client.render.model.SpriteAtlasManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.HashMap;
import java.util.Map;

@Mixin(SpriteAtlasManager.class)
public class SpriteAtlasManagerMixin {
    @ModifyVariable(
            method = "<init>",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static Map<Identifier, Identifier> engine$addAtlas(
            Map<Identifier, Identifier> value
    ) {
        Map<Identifier, Identifier> map = new HashMap<>(value);

        map.put(
                Identifier.of("engine", "engine"), Identifier.of("engine", "engine")
        );

        return map;
    }
}

