package org.lain.engine.mixin;

import net.minecraft.text.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

@Mixin(Style.class)
public interface StyleAccessor {
    @Invoker("of")
    static Style engine$of(
            Optional<TextColor> color,
            Optional<Integer> shadowColor,
            Optional<Boolean> bold,
            Optional<Boolean> italic,
            Optional<Boolean> underlined,
            Optional<Boolean> strikethrough,
            Optional<Boolean> obfuscated,
            Optional<ClickEvent> clickEvent,
            Optional<HoverEvent> hoverEvent,
            Optional<String> insertion,
            Optional<StyleSpriteSource> font
    ) {
        throw new AssertionError();
    }
}
