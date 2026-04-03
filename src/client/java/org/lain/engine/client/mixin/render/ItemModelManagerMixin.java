package org.lain.engine.client.mixin.render;

import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.mc.render.TransformationsEditorScreenKt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        TransformationsEditorScreenKt.setupAdditionalTransformationsVanilla(renderState, stack, displayContext);
    }

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"
            )
    )
    public Object engine$updateRenderModel(ItemStack instance, ComponentType componentType) {
        Identifier engineItemModel = ClientMixinAccess.INSTANCE.getEngineItemModel(instance);
        if (engineItemModel != null) {
            return engineItemModel;
        } else {
            return instance.get(componentType);
        }
    }
}
