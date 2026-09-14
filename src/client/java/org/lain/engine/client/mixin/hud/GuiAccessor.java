package org.lain.engine.client.mixin.hud;

import net.minecraft.client.gui.Gui;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiAccessor {
    @Accessor("CROSSHAIR_SPRITE")
    static ResourceLocation engine$getCrosshairTexture() {
        throw new AssertionError();
    }
}
