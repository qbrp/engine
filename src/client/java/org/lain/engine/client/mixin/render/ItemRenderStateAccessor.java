package org.lain.engine.client.mixin.render;

import net.minecraft.client.render.item.ItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemRenderState.class)
public interface ItemRenderStateAccessor {
    @Invoker("getFirstLayer")
    ItemRenderState.LayerRenderState engine$getFirstLayer();
}
