package org.lain.engine.script.lua.library.ecs

import org.lain.engine.player.EquipmentSlotId
import org.lain.engine.player.Equippable
import org.lain.engine.player.OutfitDisplay
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.lua.library.asEngineId
import org.lain.engine.world.World

fun World.tickEquippableLuaProjectSystem() {
    projectLuaComponent<Equippable>(CoreScriptComponents.EQUIPPABLE) { value, current ->
        val slot = EquipmentSlotId(value.checktable()["slot"].asEngineId())
        Equippable(slot, current?.display ?: OutfitDisplay.Separated)
    }
}
