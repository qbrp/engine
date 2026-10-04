package org.lain.engine.client.mixin.hud;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.spectator.PlayerMenuItem;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import org.lain.engine.client.mc.ClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

@Mixin(PlayerMenuItem.class)
public class PlayerMenuItemMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/SkinManager;lookupInsecure(Lcom/mojang/authlib/GameProfile;)Ljava/util/function/Supplier;"
            )
    )
    private Supplier<PlayerSkin> engine$redirectEngineSkin(
            SkinManager instance,
            GameProfile gameProfile
    ) {
        return () -> ClientMixin.INSTANCE.getPlayerSkinThreadSafe(gameProfile.getId());
    }
}
