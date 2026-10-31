package org.lain.engine.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import org.lain.engine.EnginePlayerRenderStateHolder;
import org.lain.engine.client.render.player.PlayerRenderNode;
import org.lain.engine.client.render.player.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin implements EnginePlayerRenderStateHolder {
    @Unique
    private PlayerRenderNode engine$renderState;

    @Override
    public PlayerRenderNode engine$getRenderNode() {
        return engine$renderState;
    }

    @Override
    public void engine$setRenderNode(PlayerRenderNode renderState) {
        this.engine$renderState = renderState;
    }
}
