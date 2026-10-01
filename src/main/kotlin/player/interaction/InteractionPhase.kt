package org.lain.engine.player.interaction

import org.lain.engine.script.Callbacks
import org.lain.engine.world.World

fun World.tickInteractionPhase(callbacks: Callbacks) {
    tickPlayerInputSystem(callbacks)
    tickInterruptionsSystem()

    tickVerbLookupStart()
    collectGunVerbs()
    collectWritableVerbs()
    collectSocialVerbs()
    collectTestAction()
    tickVerbLookupApply()
}
