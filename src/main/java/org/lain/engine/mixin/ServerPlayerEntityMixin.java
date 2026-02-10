package org.lain.engine.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void engine$onInit(
            MinecraftServer server,
            ServerWorld world,
            GameProfile profile,
            SyncedClientOptions clientOptions,
            CallbackInfo ci
    ) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        ServerMixinAccess.INSTANCE.onServerPlayerEntityInitialized(self);
    }
}
