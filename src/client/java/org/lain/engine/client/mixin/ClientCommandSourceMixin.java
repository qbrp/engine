package org.lain.engine.client.mixin;

import net.minecraft.client.network.ClientCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(ClientCommandSource.class)
public abstract class ClientCommandSourceMixin {
    @Shadow public abstract Collection<String> getPlayerNames();

    @Inject(
            method = "getChatSuggestions",
            at = @At("RETURN"),
            cancellable = true
    )
    public void engine$getMentionSuggestions(CallbackInfoReturnable<Collection<String>> cir) {
        Collection<String> suggestions = cir.getReturnValue();

        List<String> original = new ArrayList<>(suggestions);
        for (String s : getPlayerNames()) {
            original.add("@" + s);
        }
        cir.setReturnValue(original);
    }
}
