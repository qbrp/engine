package org.lain.engine.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;
import org.lain.engine.EnginePlayerRenderStateHolder;
import org.lain.engine.client.mc.ClientMixin;
import org.lain.engine.client.render.player.PlayerRenderNodeReference;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin implements EnginePlayerRenderStateHolder {
    @Unique
    private PlayerRenderNodeReference engine$renderState;

    @Override
    public PlayerRenderNodeReference engine$getRenderNode() {
        return engine$renderState;
    }

    @Override
    public void engine$setRenderNode(PlayerRenderNodeReference renderState) {
        this.engine$renderState = renderState;
    }

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    public void engine$copy(CallbackInfo ci) {
        AbstractClientPlayer entity = (AbstractClientPlayer)(Object) this;
        ClientMixin.INSTANCE.onClientPlayerEntityInitialized(entity);
    }

    @Inject(
            method = "getSkin",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$getSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer player = ((AbstractClientPlayer)(Object)this);
        PlayerSkin playerSkin = ClientMixin.INSTANCE.getPlayerRenderNodeSkin(player);
        if (playerSkin != null) {
            cir.setReturnValue(playerSkin);
            cir.cancel();
        }
    }
}
