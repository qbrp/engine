package org.lain.engine.data

import com.daqem.snakeyaml.engine.v2.api.LoadSettings
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.engine.container.Entries
import org.lain.engine.container.OccupiedSlots
import org.lain.engine.item.Barrel
import org.lain.engine.item.Count
import org.lain.engine.item.Flashlight
import org.lain.engine.item.GunFireState
import org.lain.engine.item.GunMagazines
import org.lain.engine.item.Writable
import org.lain.engine.script.Contents
import org.lain.engine.script.EngineId
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.script.ScriptEngine
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.toScriptComponentId
import org.lain.engine.world.Luminance

sealed interface ComponentSnapshot {
    data class Kotlin<T : Component>(
        val component: T
    ) : ComponentSnapshot

    data class Script(
        val id: ScriptComponentId,
        val value: ScriptValue
    ) : ComponentSnapshot
}

fun Component.snapshot(): ComponentSnapshot = when (this) {
    is ScriptComponent -> ComponentSnapshot.Script(
        id = type.engineId,
        value = value.copy()
    )

    else -> ComponentSnapshot.Kotlin(
        when (this) {
            is Count -> copy()
            is Entries -> Entries(items.toMutableSet())
            is Flashlight -> copy()
            is GunFireState -> copy()
            is GunMagazines -> copy()
            is Barrel -> copy()
            is Luminance -> copy()
            is OccupiedSlots -> OccupiedSlots(slots.toMutableMap())
            is Writable -> copy()
            else -> this
        }
    )
}

fun ComponentSnapshot.revive(resolver: EntityResolver, settings: ComponentLoadSettings): Component =
    when (this) {
        is ComponentSnapshot.Kotlin<*> -> component
        is ComponentSnapshot.Script -> {
            val type = settings.namespacedStorage.get().components[id] ?: error("Component type $id does not exist")
            settings.scriptEngine.createScriptComponent(value, type)
        }
    }