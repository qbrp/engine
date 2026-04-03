package org.lain.engine.container

import kotlinx.serialization.Serializable
import org.lain.engine.item.EngineItem
import org.lain.engine.storage.PersistentId
import org.lain.engine.util.component.*
import org.lain.engine.world.Location
import kotlin.let

/**
 * # Контейнеры
 * Отдельный вид сущностей, закрепляемый у предметов и игроков через якоря (например ContainerAnchor, Equipment).
 * Связь хранимых предметов является двухсторонней. Контейнер хранит в себе список предметов в Entries, хранимые предметы
 * имеют компонент ContainedIn
 *
 * - **Войд**. Базовый контейнер с компонентом Entries. Является бесконечным, можно назначать сколько угодно предметов. `Операция: AssignItem`
 * - **Слотовый**. Имеет компоненты Slots и OccupiedSlots. Количество предметов ограничено числом слотов, каждый слот может хранить только один предмет. `Операция: AssignSlot`
 * - **Фиксированный**. Принимает только определенные предметы.
 *
 * @see org.lain.engine.storage.loadWorldItem
 * @see org.lain.engine.prepareContainers
 * @since 3.5.1
 */
object Container : Component

/**
 * Хранимые предметы.
 */
data class Entries(val items: MutableList<EngineItem>) : Component

/**
 * ## Компоненты предметов
 * Крепятся к сущности в ComponentWorld
 */
data class Item(val engine: EngineItem) : Component
@Serializable data class ContainedIn(val container: EntityId) : Component
@Serializable data class ContainerAnchor(val container: EntityId) : Component

fun ReadComponentAccess.getContainerItems(container: EntityId): List<EngineItem> {
    return container.requireComponent<Entries>().items
}

fun WriteComponentAccess.createContainer(
    location: Location,
    componentState: ComponentState? = null,
    persistentId: PersistentId? = null,
    networked: Boolean = false,
    entries: MutableList<EngineItem> = mutableListOf(),
): EntityId {
    return addEntity {
        if (networked) setComponent(Networked)
        componentState?.let { copyState(it) }
        persistentId?.let { setComponent(it) }
        setComponent(location)
        setComponent(Entries(entries))
        setComponent(Container)
    }
}

fun ReadComponentAccess.collectContainedRecursive(container: EntityId): List<EngineItem> {
    val visited = mutableSetOf<EntityId>()
    val result = mutableListOf<EngineItem>()
    fun visit(container: EntityId) {
        if (!visited.add(container)) return
        val (entries) = container.getComponent<Entries>() ?: return

        for (child in entries) {
            result += child
            val anchor = child.entity.getComponent<ContainerAnchor>() ?: continue
            visit(anchor.container)
        }
    }
    visit(container)
    return result
}