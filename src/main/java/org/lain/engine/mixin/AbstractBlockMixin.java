package org.lain.engine.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class AbstractBlockMixin {
    @Inject(
            method = "onUse",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    public void engine$handleBlockUse(World world, PlayerEntity player, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (ServerMixinAccess.INSTANCE.onBlockInteraction(player, player.getEntityWorld(), hit.getBlockPos())) {
            cir.setReturnValue(ActionResult.FAIL);
            cir.cancel();
        }
    }
}
