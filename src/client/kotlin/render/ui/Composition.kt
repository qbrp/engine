package org.lain.engine.client.render.ui

import org.lain.engine.client.mc.render.EngineUiRenderPipeline.Slot
import org.lain.engine.client.render.ZeroMutableVec2
import org.lain.engine.client.render.ZeroVec2

// States
// Любой объект в дереве UI умеет хранить состояния. Это позволяет создавать неограниченное число свойств без дополнительных обёрток над фрагментами

/**
 * Глобальное состояние, хранящее **в данный момент** рисуемый виджет.
 * Его используют объекты `State`, чтобы понимать, кому конкретно передаётся состояние, чтобы записать его в подписчики
 */
object CompositionRenderContext {
    var composition: Composition? = null
    var uiContext: UiContext? = null

    fun getUiContextOrThrow() = uiContext ?: error("Контекст UI не инициализирован")
}


interface State<T> {
    fun get(): T

    companion object {
        fun recompose(listeners: Set<Composition>) {
            listeners.forEach { recompose(it, CompositionRenderContext.getUiContextOrThrow()) }
        }
    }
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
        State.recompose(listeners)
    }
}

class MutableListState<T>(initial: List<T>): State<MutableList<T>>, Iterable<T> {
    private var list: MutableList<T> = initial.toMutableList()
    private val listeners = mutableSetOf<Composition>()

    override fun get(): MutableList<T> {
        CompositionRenderContext.composition?.let {
            it.slots.add(this)
            listeners += it
        }
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
        State.recompose(listeners)
    }

    override fun iterator(): Iterator<T> = list.iterator()
}

inline fun <reified T> mutableStateOf(initial: T) = MutableState(initial)

inline fun <reified T> mutableListStateOf(vararg initial: T) = MutableListState(initial.toList())

// Composition

@JvmInline
value class CompositionId(val long: ULong) {
    companion object {
        private var last = ULong.MIN_VALUE

        fun next() = CompositionId(last++)
    }
}

data class Composition(
    val render: UiState,
    val fragmentBuilder: () -> Fragment,
    var fragment: Fragment = fragmentBuilder(),
    val slots: LinkedHashSet<State<*>> = LinkedHashSet(),
    val children: MutableList<Composition> = mutableListOf(),
    var id: CompositionId = CompositionId.next(),
    var measuredLayout: MeasuredLayout = MeasuredLayout(Size()),
    var positionedLayout: PositionedLayout = PositionedLayout(Size(), ZeroVec2(), ZeroVec2())
)

fun Composition(fragment: () -> Fragment, context: UiContext): Composition {
    fun fromFragment(fragment: Fragment, builder: () -> Fragment): Composition {
        val uiState = UiState()
        return Composition(
            uiState,
            builder,
            fragment = fragment,
            children = fragment.children.map { fromFragment(it, { it }) }.toMutableList(),
        )
    }

    val composition = fromFragment(fragment(), fragment)
    recompose(composition, context)
    return composition
}

fun recomposeLayout(composition: Composition, context: UiContext) {
    measure(context, composition)
    layout(composition, composition.render.position)
}

fun recomposeFragments(composition: Composition, context: UiContext): Fragment {
    composition.slots.clear()
    val newFragment = composition.fragmentBuilder()
    composition.fragment = newFragment

    val oldChildren = composition.children
    val newFragments = newFragment.children

    val newCompositions = mutableListOf<Composition>()

    for (fragment in newFragments) {
        val existing = oldChildren.firstOrNull { it.fragment === fragment }
        if (existing != null) {
            recomposeFragments(existing, context)
            newCompositions += existing
        } else {
            newCompositions += Composition({ fragment }, context)
        }
    }

    composition.children.clear()
    composition.children += newCompositions

    return newFragment
}

fun recomposeUiState(composition: Composition, context: UiContext) {
    updateCompositionUiState(composition, context)
    composition.children.forEach { recomposeUiState(it, context) }
    composition.fragment.onRecompose?.invoke(composition)
}

fun recompose(composition: Composition, context: UiContext) {
    recomposeFragments(composition, context)
    recomposeLayout(composition, context)
    recomposeUiState(composition, context)
}
