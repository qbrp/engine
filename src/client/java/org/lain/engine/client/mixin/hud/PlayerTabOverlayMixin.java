package org.lain.engine.client.mixin.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.lain.engine.client.mc.ClientMixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {
    @Unique
    private static final int ENGINE$PING_SLOT_WIDTH = 10;
    @Unique
    private static final int ENGINE$ROW_HEIGHT = 8;
    @Unique
    private static final int ENGINE$ANIMATION_STEP_TICKS = 10;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private Gui gui;

    @Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
    private void engine$renderOnlinePlayerLoading(
            GuiGraphics guiGraphics,
            int columnWidth,
            int columnLeft,
            int rowTop,
            PlayerInfo playerInfo,
            CallbackInfo ci
    ) {
        if (!ClientMixin.INSTANCE.isWaitingForOnlinePlayer(playerInfo.getProfile().getId())) {
            return;
        }

        ci.cancel();

        int dotCount = this.gui.getGuiTicks() / ENGINE$ANIMATION_STEP_TICKS % 3 + 1;
        String dots = ".".repeat(dotCount);
        int slotRight = columnLeft + columnWidth - 1;
        int slotLeft = slotRight - ENGINE$PING_SLOT_WIDTH;
        int textLeft = Math.max(slotLeft, slotRight - this.minecraft.font.width(dots));

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 100.0F);
        guiGraphics.enableScissor(slotLeft, rowTop, slotRight, rowTop + ENGINE$ROW_HEIGHT);
        guiGraphics.drawString(this.minecraft.font, dots, textLeft, rowTop, -1, false);
        guiGraphics.disableScissor();
        guiGraphics.pose().popPose();
    }
}
