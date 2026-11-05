package org.lain.engine;

import org.lain.engine.client.render.player.PlayerRenderNodeReference;

public interface EnginePlayerRenderStateHolder {
    PlayerRenderNodeReference engine$getRenderNode();
    void engine$setRenderNode(PlayerRenderNodeReference node);
}
