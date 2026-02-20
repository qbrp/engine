package org.lain.engine.client.util

import org.lain.engine.player.EnginePlayer

class PlayerTickException(val player: EnginePlayer, error: Throwable) : RuntimeException(error)