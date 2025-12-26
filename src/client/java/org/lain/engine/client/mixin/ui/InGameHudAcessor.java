package org.lain.engine.client.mixin.ui;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(InGameHud.class)
public interface InGameHudAcessor {
    @Accessor("CROSSHAIR_TEXTURE")
    static Identifier engine$getCrosshairTexture() {
        throw new AssertionError();
    }
}
