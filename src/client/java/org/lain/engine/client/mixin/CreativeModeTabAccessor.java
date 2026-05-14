package org.lain.engine.client.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Supplier;

@Mixin(CreativeModeTab.class)
public interface CreativeModeTabAccessor {
    @Accessor("iconItemStack")
    void engine$setIcon(ItemStack icon);

    @Invoker("<init>")
    static CreativeModeTab newInstance(
            CreativeModeTab.Row row,
            int column,
            CreativeModeTab.Type type,
            Component displayName,
            Supplier<ItemStack> iconSupplier,
            CreativeModeTab.DisplayItemsGenerator entryCollector
    ) {
        throw new AssertionError();
    }
}
