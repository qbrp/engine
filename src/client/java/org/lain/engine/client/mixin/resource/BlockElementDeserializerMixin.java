package org.lain.engine.client.mixin.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.lain.engine.client.resources.ModelLoaderKt;
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
        if (f != 0.0f && Mth.abs(f) != 22.5f && Mth.abs(f) != 45.0f && Mth.abs(f) != 90f) {
            ModelLoaderKt.getMC_LOGGER().warn("Invalid rotation {} found, only -45/-22.5/0/22.5/45 allowed", f);
        }
        cir.setReturnValue(f);
        cir.cancel();
    }
}
