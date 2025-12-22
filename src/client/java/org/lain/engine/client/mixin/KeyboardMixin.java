package org.lain.engine.client.mixin;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.mc.KeybindManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Shadow @Final private MinecraftClient client;

    @Redirect(
            method = "onKey",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.PUTFIELD,
                    target = "Lnet/minecraft/client/option/GameOptions;hudHidden:Z"
            )
    )
    private void engine$cancelHidingHud(GameOptions instance, boolean value) {}

    @Redirect(
            method = "onKey",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/InputUtil;fromKeyCode(II)Lnet/minecraft/client/util/InputUtil$Key;"
            )
    )
    public InputUtil.Key engine$invokeChatScreenKeybindings(int keyCode, int scanCode) {
        InputUtil.Key key = InputUtil.fromKeyCode(keyCode, scanCode);
        if (client.currentScreen instanceof ChatScreen) {
            KeybindManager keybindManager = KeybindManager.INSTANCE;
            KeyBinding[] keybindings = new KeyBinding[] {
                    keybindManager.getADJUST_CHAT_VOLUME().getMinecraft(),
                    keybindManager.getDECREASE_CHAT_VOLUME().getMinecraft(),
                    keybindManager.getRESET_CHAT_VOLUME().getMinecraft()
            };

            for (KeyBinding keybinding : keybindings) {
                keybinding.setPressed(KeyBindingHelper.getBoundKeyOf(keybinding) == key);
            }
        }
        return key;
    }

    @Inject(
            method = "onKey",
            at = @At(
                    value = "HEAD"
            )
    )
    public void engine$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        ClientMixinAccess.INSTANCE.onKey(key);
    }
}
