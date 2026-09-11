package org.lain.engine.chat.acoustic

import org.lain.engine.player.EnginePlayer
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.math.Pos
import org.lain.engine.world.WorldId

interface AcousticSimulator {
    suspend fun simulateSingleSource(
        world: WorldId,
        pos: Pos,
        volume: Float,
        maxVolume: Float,
        attenuation: Float,
        exceptionHandler: (Throwable) -> Unit
    ): AcousticSimulationResult

    companion object {
        val DUMMY = object : AcousticSimulator {
            override suspend fun simulateSingleSource(
                world: WorldId,
                pos: Pos,
                volume: Float,
                maxVolume: Float,
                attenuation: Float,
                exceptionHandler: (Throwable) -> Unit
            ): AcousticSimulationResult = AcousticSimulationResult.DUMMY
        }
    }
}

interface AcousticSimulationResult {
    fun debug(player: EnginePlayer, handler: ServerHandler, radius: Float)
    fun getVolume(pos: Pos): Float?
    fun finish()

    companion object {
        val DUMMY = object : AcousticSimulationResult {
            override fun debug(
                player: EnginePlayer,
                handler: ServerHandler,
                radius: Float
            ) {}

            override fun getVolume(pos: Pos): Float? = null

            override fun finish() {}
        }
    }
}
