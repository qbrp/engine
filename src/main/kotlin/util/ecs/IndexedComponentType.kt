package org.lain.engine.util.ecs

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType

abstract class IndexedComponentType<T : Component>(
    final override val id: String
) : ComponentType<T> {
    val idx: Int = indexOf(id)

    companion object {
        private val indexes = LinkedHashMap<String, Int>()

        @Synchronized
        fun indexOf(id: String): Int {
            return indexes.getOrPut(id) { indexes.size }
        }
    }
}
fun <T : Component> ComponentType<T>.castIndexed() = (this as? IndexedComponentType) ?: error("Component type must be indexed (subclass of IndexedComponentType)")

class EngineComponentType<T : Component>(id: String) : IndexedComponentType<T>(id) {
    override fun toString(): String = "KotlinComponentType($idx, $id)"
}