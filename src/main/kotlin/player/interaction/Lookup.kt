package org.lain.engine.player.interaction

import org.lain.cyberia.ecs.Component
import kotlin.reflect.KClass

data class VerbVariant(
    val verb: Verb,
    val action: InputAction,
) : Component

data class Verb(val type: VerbType, val component: () -> Component)

@Suppress("UNCHECKED_CAST")
fun <T : InputAction> Set<InputAction>.forAction(
    actionClass: KClass<T>,
    statement: (T) -> Unit
) {
    for (action in this) {
        if (actionClass.isInstance(action)) {
            statement(action as T)
        }
    }
}

inline fun <reified T : InputAction> Set<InputAction>.forAction(
    noinline statement: (T) -> Unit,
) {
    forAction(T::class, statement)
}

val GUN_SHOOT_VERB = VerbType(
    "shoot",
    "Нажать на спусковой крючок",
)

val GUN_TOGGLE_MODE_VERB = VerbType(
    "gun_toggle_mode",
    "Сменить режим огня",
)

val GUN_BARREL_AMMO_LOAD_VERB = VerbType(
    "barrel_ammo_load",
    "Загрузить патроны в патронник",
)

val HAIL_VERB = VerbType(
    "hail",
    "Окликнуть",
    priority = 10
)

val GIVE_AWAY = VerbType(
    "give_away",
    "Передать предмет",
    priority = -5
)

val WRITEABLE_OPEN_VERB = VerbType(
    "writable_open",
    "Открыть для чтения"
)

val SLOT_MERGE_VERB = VerbType("slot_merge", "Объединить предметы")