package org.lain.engine.mixin;

import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerPlayerGameMode.class)
public interface ServerPlayerGameModeAccessor {
    @Invoker("setGameModeForPlayer")
    void engine$setGameModeForPlayer(GameType gameType, @Nullable GameType previousGameType);
}
