package org.lain.engine.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldMixin {
    @Shadow public abstract BlockState getBlockState(BlockPos pos);

    @Inject(
            method = "removeBlock",
            at = @At(value = "HEAD")
    )
    public void engine$removeBlock(BlockPos pos, boolean move, CallbackInfoReturnable<Boolean> cir) {
        ServerMixinAccess.INSTANCE.onBlockRemoved((World)((Object)this), getBlockState(pos), pos);
    }

    @Inject(
            method = "breakBlock",
            at = @At(value = "HEAD")
    )
    public void engine$breakBlock(BlockPos pos, boolean drop, Entity breakingEntity, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        ServerMixinAccess.INSTANCE.onBlockRemoved((World)((Object)this), getBlockState(pos), pos);
    }
}
