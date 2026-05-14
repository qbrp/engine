package org.lain.engine.client.mixin.ui;

import net.minecraft.client.gui.Gui;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiAccessor {
    @Accessor("CROSSHAIR_SPRITE")
    static Identifier engine$getCrosshairTexture() {
        throw new AssertionError();
    }
}
