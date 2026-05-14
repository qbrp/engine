package org.lain.engine.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.mc.DeveloperModeActionsKt;
import org.lain.engine.client.mc.KeybindManager;
import org.lain.engine.client.mc.render.InteractionSelectionScreen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
    @Shadow @Final private Minecraft minecraft;

    @Redirect(
            method = "keyPress",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.PUTFIELD,
                    target = "Lnet/minecraft/client/Options;hideGui:Z"
            )
    )
    private void engine$cancelHidingHud(Options instance, boolean value) {}

    @Redirect(
            method = "keyPress",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/InputConstants;getKey(Lnet/minecraft/client/input/KeyEvent;)Lcom/mojang/blaze3d/platform/InputConstants$Key;"
            )
    )
    public InputConstants.Key engine$invokeChatScreenKeybindings(net.minecraft.client.input.KeyEvent keyEvent) {
        InputConstants.Key key = InputConstants.getKey(keyEvent);
        KeybindManager keybindManager = ClientMixinAccess.INSTANCE.getKeybindManager();
        KeyMapping[] keybindings = new KeyMapping[] {};
        if (minecraft.screen instanceof ChatScreen) {
            keybindings = new KeyMapping[]{
                    keybindManager.getAdjustChatVolume().getMinecraft(),
                    keybindManager.getDecreaseChatVolume().getMinecraft(),
                    keybindManager.getResetChatVolume().getMinecraft()
            };
        } else if (minecraft.screen instanceof InteractionSelectionScreen) {
            keybindings = new KeyMapping[]{
                    keybindManager.getBase().getMinecraft(),
                    keybindManager.getAttack().getMinecraft(),
                    keybindManager.getTakeOffEquip().getMinecraft()
            };
        }

        for (KeyMapping keybinding : keybindings) {
            keybinding.setDown(KeyBindingHelper.getBoundKeyOf(keybinding) == key);
        }

        return key;
    }

    @Inject(
            method = "keyPress",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    public void engine$onKey(long l, int i, KeyEvent keyEvent, CallbackInfo ci) {
        if (DeveloperModeActionsKt.onKeyDeveloperMode(keyEvent.key())) {
            ci.cancel();
        }
    }
}
