package org.lain.engine.client.mixin.render;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.render.item.RenderStateKt;
import org.lain.engine.client.render.ui.TransformationsEditorScreenKt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(ItemModelResolver.class)
public class ItemModelManagerMixin {
    @Shadow @Final private Function<Identifier, ItemModel> modelGetter;

    @Inject(
            method = "updateForTopItem",
            at = @At(
                    value = "TAIL"
            )
    )
    public void engine$setTransformations(ItemStackRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, Level world, ItemOwner heldItemContext, int seed, CallbackInfo ci) {
        RenderStateKt.setupAdditionalTransformationsVanilla(renderState, stack, displayContext);
    }

    @Redirect(
            method = "appendItemLayers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"
            )
    )
    public Object engine$updateRenderModel(ItemStack instance, DataComponentType componentType) {
        Identifier engineItemModel = ClientMixinAccess.INSTANCE.getEngineItemModel(instance);
        if (engineItemModel != null) {
            return engineItemModel;
        } else {
            return instance.get(componentType);
        }
    }
}
