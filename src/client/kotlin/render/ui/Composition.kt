package org.lain.engine.client.render.ui

import org.lain.engine.client.render.ZeroVec2
import kotlin.reflect.KProperty

// States
// Любой объект в дереве UI умеет хранить состояния. Это позволяет создавать неограниченное число свойств без дополнительных обёрток над фрагментами

/**
 * Глобальное состояние, хранящее **в данный момент** рисуемый виджет и контекст UI.
 * Его используют объекты `State`, чтобы понимать, кому конкретно передаётся состояние, чтобы записать его в подписчики
 */
object CompositionRenderContext {
    var composition: Composition? = null
    var uiContext: UiContext? = null

    /**
     * По контракту, функция рендеринга должна перед отрисовкой **каждой** композиции вызывать в начале этот метод
     */
    fun startRendering(composition: Composition, context: UiContext? = null) {
        context?.let { this.uiContext = it }
        this.composition = composition
        composition.slotLastIndex = 0
    }

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

    override fun toString(): String {
        return value.toString()
    }

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

class Remember<T : State<*>>(val builder: () -> T) {
    private var state: T? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return state ?: run {
            val composition = CompositionRenderContext.composition
                ?: error("Render cycle is not active")

            val index = composition.slotLastIndex
            val slots = composition.slots

            if (index < slots.size) {
                slots[index] as T
            } else {
                val st = builder()
                slots.add(st)
                st
            }
        }.also { state = it }
    }
}

fun <T : State<*>> remember(builder: () -> T) = Remember(builder)

// RootComposition

@JvmInline
value class CompositionId(val long: ULong) {
    companion object {
        private var last = ULong.MIN_VALUE

        fun next() = CompositionId(last++)
    }
}

class Composition internal constructor(
    val render: UiState,
    val fragmentBuilder: () -> Fragment,
    var fragment: Fragment = fragmentBuilder(),
    val slots: MutableList<State<*>> = mutableListOf(),
    var slotLastIndex: Int = 0,
    var children: MutableList<Composition> = mutableListOf(),
    var id: CompositionId = CompositionId.next(),
    var measuredLayout: MeasuredLayout = MeasuredLayout(Size()),
    var positionedLayout: PositionedLayout = PositionedLayout(Size(), ZeroVec2(), ZeroVec2()),
    var parent: Composition? = null
)

fun Composition(builder: () -> Fragment): Composition {
    val uiState = UiState()
    val composition = Composition(
        uiState,
        builder
    )
    CompositionRenderContext.startRendering(composition)
    composition.fragment = builder()
    composition.children = composition.fragment.children.map { Composition { it } }.toMutableList()
    return composition
}

fun recomposeLayout(composition: Composition, constraints: Size, context: UiContext) {
    measure(composition, context, constraints)
    layout(composition)
}

fun recomposeFragments(composition: Composition, context: UiContext): Fragment {
    val newFragment = composition.fragmentBuilder()
    composition.fragment = newFragment

    val oldChildren = composition.children
    val newFragments = newFragment.children

    val newCompositions = mutableListOf<Composition>()

    for (fragment in newFragments) {
        val existing = oldChildren.firstOrNull { it.fragment == fragment }
        if (existing != null) {
            recomposeFragments(existing, context)
            newCompositions += existing
        } else {
            newCompositions += Composition({ fragment })
        }
    }

    composition.children.clear()
    composition.children += newCompositions
    composition.children.forEach { it.parent = composition }

    return newFragment
}

fun recomposeUiState(composition: Composition, context: UiContext) {
    updateCompositionUiState(composition, context)
    composition.children.forEach { recomposeUiState(it, context) }
    composition.fragment.onRecompose?.invoke(composition)
}

fun recompose(composition: Composition, constraints: Size, context: UiContext) {
    recomposeFragments(composition, context)
    recomposeLayout(composition, constraints, context)
    recomposeUiState(composition, context)
}

/**
 * Рекомпозиция. Взять ограничения у родительской композиции или окна
 */
fun recompose(composition: Composition, context: UiContext) {
    recompose(composition, composition.parent?.measuredLayout?.measuredSize ?: context.windowSize, context)
}
