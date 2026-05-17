package org.lain.engine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin {
    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN")
    )
    public void engine$setBlockState(BlockPos blockPos, BlockState blockState, int i, int j, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() == true && blockState.isAir()) {
            ServerMixinAccess.INSTANCE.onBlockRemoved((Level) (Object)this, blockPos);
        }
    }
}
