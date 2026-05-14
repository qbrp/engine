package org.lain.engine.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockStateBaseMixin {
    @Inject(
            method = "useWithoutItem",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    public void engine$handleBlockUse(Level world, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (ServerMixinAccess.INSTANCE.onBlockInteraction(player, player.level(), hit.getBlockPos())) {
            cir.setReturnValue(InteractionResult.FAIL);
            cir.cancel();
        }
    }
}
