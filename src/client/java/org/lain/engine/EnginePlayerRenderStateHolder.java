package org.lain.engine;

import org.lain.engine.client.render.player.PlayerRenderNode;

public interface EnginePlayerRenderStateHolder {
    PlayerRenderNode engine$getRenderNode();
    void engine$setRenderNode(PlayerRenderNode node);
}
