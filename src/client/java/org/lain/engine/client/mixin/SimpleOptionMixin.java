package org.lain.engine.client.mixin;

import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.lain.engine.client.mc.MinecraftChat;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(SimpleOption.class)
public class SimpleOptionMixin {
    @Shadow @Final private Text text;

    @Inject(
            method = "setValue",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/option/SimpleOption;value:Ljava/lang/Object;",
                    opcode = Opcodes.PUTFIELD
            )
    )
    void engine$setValue(Object value, CallbackInfo ci) {
        if (Objects.equals(((TranslatableTextContent) this.text.getContent()).getKey(), "options.chat.scale")) {
            MinecraftChat.INSTANCE.getChannelsBar().measure();
        } else if (Objects.equals(((TranslatableTextContent) this.text.getContent()).getKey(), "options.chat.width")) {
            MinecraftChat.INSTANCE.getChannelsBar().measure();
        }
    }
}
