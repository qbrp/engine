package org.lain.engine.client.render.ui

// States
// Любой объект в дереве UI умеет хранить состояния. Это позволяет создавать неограниченное число свойств без дополнительных обёрток над фрагментами

/**
 * Глобальное состояние, хранящее **в данный момент** рисуемый виджет.
 * Его используют объекты `State`, чтобы понимать, кому конкретно передаётся состояние, чтобы записать его в подписчики
 */
object CompositionRenderContext {
    val composition: Composition? = null
}

interface State<T> {
    fun get(): T
}

class MutableState<T>(initial: T): State<T> {
    private var value: T = initial
    private val listeners = mutableSetOf<Composition>()

    override fun get(): T {
        CompositionRenderContext.composition?.let { listeners += it }
        return value
    }

    fun set(value: T) {
        this.value = value
        listeners.forEach { recompose(it) }
    }
}

class MutableListState<T>(initial: List<T>): State<MutableList<T>>, Iterable<T> {
    private var list: MutableList<T> = initial.toMutableList()
    private val listeners = mutableSetOf<Composition>()

    override fun get(): MutableList<T> {
        CompositionRenderContext.composition?.let { listeners += it }
        return list
    }

    fun set(value: T, index: Int) {
        list[index] = value
        update()
    }

    fun add(value: T) {
        list.add(value)
        update()
    }

    fun remove(value: T) {
        list.remove(value)
        update()
    }

    private fun update() {
        listeners.forEach { recompose(it) }
    }

    override fun iterator(): Iterator<T> = list.iterator()
}

inline fun <reified T> mutableStateOf(initial: T) = MutableState(initial)

inline fun <reified T> mutableListStateOf(vararg initial: T) = MutableListState(initial.toList())

// Composition

data class Composition(
    val render: UiState,
    var fragment: Fragment,
    val slots: MutableSet<State<*>>,
    var recompose: Boolean = false,
    val children: MutableList<Composition>
)

fun recompose(composition: Composition) {
    composition.recompose = true
    composition.children.forEach { recompose(it) }
}