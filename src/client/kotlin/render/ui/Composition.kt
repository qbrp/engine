package org.lain.engine.client.render.ui

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
        recompose(listeners, CompositionRenderContext.getUiContextOrThrow())
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
        recompose(listeners, CompositionRenderContext.getUiContextOrThrow())
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
    val slots: LinkedHashSet<State<*>> = LinkedHashSet(),
    val children: MutableList<Composition> = mutableListOf(),
    var lastFragment: Fragment = fragmentBuilder(),
    var id: CompositionId = CompositionId.next(),
    var clear: Boolean = false,
    val constraintsSize: Size
)

fun recompose(compositions: Set<Composition>, context: UiContext) {
    val rebuilt = mutableSetOf<CompositionId>()
    fun rebuild(composition2: Composition) {
        composition2.slots.clear()
        composition2.lastFragment = composition2.fragmentBuilder()
        composition2.children.forEach { rebuild(it) }
        rebuilt.add(composition2.id)
    }

    fun apply(parent: Composition?, fragment: PreparedFragment) {
        fragment.composition.apply {
            render.applyFragment(fragment, context)
            if (parent != null && fragment.fragment !in parent.children.map { it.lastFragment }) {
                parent.children.add(this)
            }
        }
        fragment.children.forEach { apply(fragment.composition, it) }
        fragment.composition.lastFragment.onMeasure?.let { it(fragment.composition) }
    }

    compositions.forEach { composition ->
        if (rebuilt.contains(composition.id)) return@forEach
        rebuild(composition)
        val measured = measure(context, composition, composition.constraintsSize)
        val layout = layout(measured, composition.render.position)

        apply(null, layout)
    }
}
