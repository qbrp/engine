package org.lain.engine.script.lua.library.ecs

import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.SelectedLook
import org.lain.engine.script.EngineId
import org.lain.engine.script.lua.library.ComponentBinding
import org.lain.engine.script.lua.library.toLuaValue

fun AppliedCharacterBinding(cache: Boolean = true) = ComponentBinding(
    id = EngineId("core/character/applied"),
    componentClass = AppliedCharacter::class,
    cache = cache,
    toLuaValue = { _, _, component -> component.character.data.toLuaValue() },
)

fun AppliedLookBinding(cache: Boolean = true) = ComponentBinding(
    id = EngineId("core/character/applied_look"),
    componentClass = SelectedLook::class,
    cache = cache,
    toLuaValue = { _, _, component -> component.look.toLuaValue() },
)
