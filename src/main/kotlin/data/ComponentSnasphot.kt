package org.lain.engine.data

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.engine.container.Entries
import org.lain.engine.container.OccupiedSlots
import org.lain.engine.item.Barrel
import org.lain.engine.item.Count
import org.lain.engine.item.Flashlight
import org.lain.engine.item.GunFireState
import org.lain.engine.item.GunMagazines
import org.lain.engine.item.Writable
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.script.ScriptEngine
import org.lain.engine.script.ScriptValue
import org.lain.engine.world.Luminance

sealed interface ComponentSnapshot {
    val id: String

    data class Kotlin<T : Component>(
        val component: T
    ) : ComponentSnapshot {
        override val id: String
            get() = componentTypeOf(component).id
    }

    data class Script(
        val scriptId: ScriptComponentId,
        val value: ScriptValue
    ) : ComponentSnapshot {
        override val id: String
            get() = scriptId.toString()
    }
}

fun ScriptComponent.snapshot() = ComponentSnapshot.Script(
    scriptId = type.engineId,
    value = value.copy()
)

fun Component.snapshot(): ComponentSnapshot = when (this) {
    is ScriptComponent -> snapshot()

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

data class ComponentReviveSettings(
    val namespacedStorage: NamespacedStorageAccess,
    val scriptEngine: ScriptEngine,
)

fun ComponentSnapshot.revive(resolver: EntityResolver, settings: ComponentReviveSettings): Component =
    when (this) {
        is ComponentSnapshot.Kotlin<*> -> component
        is ComponentSnapshot.Script -> {
            val type = settings.namespacedStorage.get().components[scriptId] ?: error("Component type $scriptId does not exist")
            settings.scriptEngine.createScriptComponent(value, type)
        }
    }