package org.lain.engine.player.interaction

import org.lain.cyberia.ecs.Component
import kotlin.reflect.KClass

data class Verb(
    val id: VerbId,
    val name: String,
    val priority: Int = 10,
    val holdsInput: InputAction? = null,
    val createCommand: () -> Component,
)

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
