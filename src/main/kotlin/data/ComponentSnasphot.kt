package org.lain.engine.data

import kotlinx.serialization.Serializable
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
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.ScriptEngine
import org.lain.engine.script.ScriptValue
import org.lain.engine.world.Luminance

@Serializable
sealed interface ComponentSnapshot {
    val id: String

    @Serializable
    data class Kotlin<T : Component>(
        val component: T
    ) : ComponentSnapshot {
        override val id: String
            get() = componentTypeOf(component).id
    }

    @Serializable
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

class ScriptComponentFreezeException(val component: ScriptComponent, cause: Exception) : RuntimeException(cause)

fun Component.snapshot(): ComponentSnapshot = when (this) {
    is ScriptComponent -> try {
        snapshot()
    } catch (e: Exception) {
        throw ScriptComponentFreezeException(this, e)
    }

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

fun ComponentReviveSettings.resolveScriptComponentType(id: ScriptComponentId): ScriptComponentType {
    return namespacedStorage.get().components[id] ?: error("Component type $id does not exist")
}

fun ComponentSnapshot.Script.revive(
    resolver: EntityResolver,
    settings: ComponentReviveSettings,
    type: ScriptComponentType = settings.resolveScriptComponentType(scriptId)
): ScriptComponent {
    return settings.scriptEngine.createScriptComponent(value, type)
}

fun ComponentSnapshot.revive(resolver: EntityResolver, settings: ComponentReviveSettings): Component =
    when (this) {
        is ComponentSnapshot.Kotlin<*> -> component
        is ComponentSnapshot.Script -> revive(resolver, settings)
    }