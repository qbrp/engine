package org.lain.engine.client.mixin.resource;

import com.google.gson.JsonObject;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.math.MathHelper;
import org.lain.engine.client.resources.ModelLoaderKt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.render.model.json.Transformation$Deserializer")
public class TransformationDeserializerMixin {
    @Redirect(
            method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/render/model/json/Transformation;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/MathHelper;clamp(FFF)F"
            )
    )
    public float engine$removeClamping(float value, float min, float max) {
        if (value < min || value > max) {
            float clamped = MathHelper.clamp(value, min, max);
            ModelLoaderKt.getMC_LOGGER().warn("Skipped value clamping: {} -> {}", value, clamped);
        }
        return value;
    }
}
