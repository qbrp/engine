package org.lain.engine.client.mixin;

import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Supplier;

@Mixin(ItemGroup.class)
public interface ItemGroupAccessor {
    @Accessor("icon")
    void engine$setIcon(ItemStack icon);

    @Invoker("<init>")
    static ItemGroup newInstance(ItemGroup.Row row, int column, ItemGroup.Type type, Text displayName, Supplier<ItemStack> iconSupplier, ItemGroup.EntryCollector entryCollector) {
        throw new AssertionError();
    }
}
