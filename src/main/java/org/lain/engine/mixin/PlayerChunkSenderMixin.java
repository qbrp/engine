package org.lain.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerChunkSender.class)
public class PlayerChunkSenderMixin {
    @Inject(
            method = "sendNextChunks",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$sendChunkBatches(ServerPlayer player, CallbackInfo ci) {
        if (!ServerMixinAccess.INSTANCE.inEnginePlayer(player)) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "sendNextChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/PlayerChunkSender;sendChunk(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;)V"
            )
    )
    private void sendInitialAttachmentData(ServerGamePacketListenerImpl serverGamePacketListenerImpl, ServerLevel serverLevel, LevelChunk levelChunk, Operation<Void> original) {
        original.call(serverGamePacketListenerImpl, serverLevel, levelChunk);
        ServerMixinAccess.INSTANCE.onChunkDataSent(levelChunk, serverGamePacketListenerImpl.player);
    }
}
