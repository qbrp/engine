package org.lain.engine.client.mixin.render;

import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiRenderer.class)
public interface GuiRendererAccessor {
    @Invoker("onItemAtlasChanged")
    void engine$onItemAtlasChanged();
}
