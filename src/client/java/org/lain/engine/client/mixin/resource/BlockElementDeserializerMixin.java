package org.lain.engine.client.mixin.resource;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import org.lain.engine.client.resources.ModelLoaderPluginKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.block.model.BlockElement$Deserializer")
public class BlockElementDeserializerMixin {
    @Inject(
            method = "getAngle",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    public void engine$removeAssertion(JsonObject jsonObject, CallbackInfoReturnable<Float> cir) {
        float f = GsonHelper.getAsFloat(jsonObject, "angle");
        cir.setReturnValue(f);
        cir.cancel();
    }
}
