package org.lain.engine.client.mixin.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.render.model.json.ModelElement;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.math.MathHelper;
import org.lain.engine.client.resources.ModelLoaderKt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.render.model.json.ModelElement$Deserializer")
public class ModelElementDeserializerMixin {
    @Inject(
            method = "deserializeRotationAngle",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    public void engine$removeAssertion(JsonObject object, CallbackInfoReturnable<Float> cir) {
        float f = JsonHelper.getFloat((JsonObject)object, (String)"angle");
        cir.setReturnValue(f);
        if (f != 0.0f && MathHelper.abs((float)f) != 22.5f && MathHelper.abs((float)f) != 45.0f) {
            ModelLoaderKt.getMC_LOGGER().warn("Invalid rotation {} found, only -45/-22.5/0/22.5/45 allowed", f);
        }
        cir.cancel();
    }
}
