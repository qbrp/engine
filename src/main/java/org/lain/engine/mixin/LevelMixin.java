package org.lain.engine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.lain.engine.mc.CommonMixin;
import org.lain.engine.mc.ServerMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin {
    @Inject(
            target = @Desc(
                    value = "setBlock",
                    args = {BlockPos.class, BlockState.class, int.class, int.class},
                    ret = boolean.class
            ),
            at = @At("RETURN")
    )
    public void engine$setBlockState(BlockPos blockPos, BlockState blockState, int i, int j, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() == true && blockState.isAir()) {
            CommonMixin.INSTANCE.onAirBlockPlaced((Level) (Object)this, blockPos);
        }
    }
}
