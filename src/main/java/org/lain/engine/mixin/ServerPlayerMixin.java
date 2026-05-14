package org.lain.engine.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void engine$onInit(
            MinecraftServer server,
            ServerLevel world,
            GameProfile profile,
            ClientInformation clientOptions,
            CallbackInfo ci
    ) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        ServerMixinAccess.INSTANCE.onServerPlayerInitialized(self);
    }
}
