package org.lain.engine.script.lua.library.ecs

import org.lain.engine.player.Equippable
import org.lain.engine.player.OutfitDisplay
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.world.World

fun World.pullEquippable() {
    projectLuaComponent<Equippable>(CoreScriptComponents.EQUIPPABLE) { value, current ->
        //Equippable(current?.display ?: OutfitDisplay.Separated)
        Equippable(OutfitDisplay.Separated)
    }
}
