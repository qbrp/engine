package org.lain.engine;

import org.lain.engine.client.render.player.EnginePlayerRenderState;
import org.lain.engine.client.render.player.RenderStateComponent;

public interface EnginePlayerRenderStateHolder {
    EnginePlayerRenderState engine$getPlayerRenderState();
    void engine$setPlayerRenderState(EnginePlayerRenderState renderState);
}
