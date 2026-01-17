package org.lain.engine.mixin;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(
            method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("RETURN")
    )
    public void engine$place(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (cir.getReturnValue() == ActionResult.SUCCESS) {
            World world = context.getWorld();
            BlockPos pos = context.getBlockPos();
            ServerMixinAccess.INSTANCE.onBlockAdded(world, pos, world.getBlockState(pos));
        }
    }
}
