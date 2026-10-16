package org.lain.engine.player.interaction

import org.lain.engine.EngineSimulation
import org.lain.engine.script.Callbacks
import org.lain.engine.world.World

fun World.tickInteractionPhase(callbacks: Callbacks, extension: EngineSimulation.SimulationTickExtension) {
    tickPlayerInputSystem(callbacks, extension)
    tickInterruptionsSystem()

    tickVerbLookupStart()
    collectGunVerbs()
    collectWritableVerbs()
    collectSocialVerbs()
    collectTestAction()
}
