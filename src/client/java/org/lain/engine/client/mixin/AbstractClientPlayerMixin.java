package org.lain.engine.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import org.lain.engine.EnginePlayerRenderStateHolder;
import org.lain.engine.client.render.player.EnginePlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin implements EnginePlayerRenderStateHolder {
    @Unique
    private EnginePlayerRenderState engine$renderState;

    @Override
    public EnginePlayerRenderState engine$getPlayerRenderState() {
        return engine$renderState;
    }

    @Override
    public void engine$setPlayerRenderState(EnginePlayerRenderState renderState) {
        this.engine$renderState = renderState;
    }
}
