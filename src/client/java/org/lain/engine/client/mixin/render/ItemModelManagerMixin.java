package org.lain.engine.client.mixin.render;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.model.BasicItemModel;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.lain.engine.client.mc.render.AdditionalTransformationsBank;
import org.lain.engine.client.mc.render.Transformations;
import org.lain.engine.client.mc.render.TransformationsEditorScreenKt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.function.Function;

@Mixin(ItemModelManager.class)
public class ItemModelManagerMixin {
    @Shadow @Final private Function<Identifier, ItemModel> modelGetter;

    @Inject(
            method = "update",
            at = @At(
                    value = "TAIL"
            )
    )
    public void engine$setTransformations(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, World world, HeldItemContext heldItemContext, int seed, CallbackInfo ci) {
        Transformations transformations = AdditionalTransformationsBank.INSTANCE.get(Objects.requireNonNull(stack.get(DataComponentTypes.ITEM_MODEL)));
        if (transformations != null) {
            ItemRenderState.LayerRenderState[] layers = ((ItemRenderStateAccessor)renderState).engine$getLayers();
            for (ItemRenderState.LayerRenderState layer : layers) {
                TransformationsEditorScreenKt.setAdditionalTransformations(layer, transformations, displayContext);
            }
        }
    }
}
