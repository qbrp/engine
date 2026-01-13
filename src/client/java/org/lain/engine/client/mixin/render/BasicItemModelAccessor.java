package org.lain.engine.client.mixin.render;

import net.minecraft.client.render.item.model.BasicItemModel;
import net.minecraft.client.render.model.ModelSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BasicItemModel.class)
public interface BasicItemModelAccessor {
    @Accessor("settings")
    ModelSettings engine$getModelSettings();
}
