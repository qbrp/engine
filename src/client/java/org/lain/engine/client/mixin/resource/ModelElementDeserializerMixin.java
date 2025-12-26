package org.lain.engine.client.mixin.resource;

import com.google.gson.JsonObject;
import net.minecraft.client.render.model.json.ModelElement;
import net.minecraft.util.JsonHelper;
import org.spongepowered.asm.mixin.Mixin;
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
        cir.setReturnValue(JsonHelper.getFloat((JsonObject)object, (String)"angle"));
        cir.cancel();
    }
}
