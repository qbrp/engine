package org.lain.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.impl.attachment.AttachmentTargetImpl;
import net.fabricmc.fabric.impl.attachment.sync.AttachmentChange;
import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ChunkDataSender.class)
public class ChunkDataSenderMixin {
    @Inject(
            method = "sendChunkBatches",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$sendChunkBatches(ServerPlayerEntity player, CallbackInfo ci) {
        if (!ServerMixinAccess.INSTANCE.inEnginePlayer(player)) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "sendChunkBatches",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ChunkDataSender;sendChunkData(Lnet/minecraft/server/network/ServerPlayNetworkHandler;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/WorldChunk;)V"
            )
    )
    private void sendInitialAttachmentData(ServerPlayNetworkHandler handler, ServerWorld world, WorldChunk chunk, Operation<Void> original, ServerPlayerEntity player) {
        original.call(handler, world, chunk);
        ServerMixinAccess.INSTANCE.onChunkDataSent(chunk, player);
    }
}
