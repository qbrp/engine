package org.lain.engine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(
            method = "place",
            at = @At("RETURN")
    )
    public void engine$place(BlockPlaceContext blockPlaceContext, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() == InteractionResult.SUCCESS) {
            Level world = blockPlaceContext.getLevel();
            BlockPos pos = blockPlaceContext.getClickedPos();
            ServerMixinAccess.INSTANCE.onBlockAdded(blockPlaceContext, world, pos, world.getBlockState(pos));
        }
    }
}
