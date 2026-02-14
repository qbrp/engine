package org.lain.engine.client.mixin.render;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(GuiRenderer.class)
public interface GuiRendererAccessor {
    @Invoker("onItemAtlasChanged")
    void engine$onItemAtlasChanged();
}
