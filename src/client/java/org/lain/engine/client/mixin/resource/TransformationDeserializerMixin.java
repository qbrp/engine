package org.lain.engine.client.mixin.resource;

import net.minecraft.util.Mth;
import org.lain.engine.client.resources.ModelLoaderPluginKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.renderer.block.model.ItemTransform$Deserializer")
public class TransformationDeserializerMixin {
    @Redirect(
            method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/ItemTransform;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;clamp(FFF)F"
            )
    )
    public float engine$removeClamping(float value, float min, float max) {
        return value;
    }
}
