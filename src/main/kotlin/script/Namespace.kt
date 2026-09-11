package org.lain.engine.script

import kotlinx.serialization.Serializable
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.player.interaction.ProgressionAnimation
import org.lain.engine.player.interaction.ProgressionAnimationId
import org.lain.engine.util.Operation
import org.lain.engine.util.OperationId
import org.lain.engine.world.SoundEvent
import org.lain.engine.world.SoundEventId

@JvmInline
@Serializable
value class NamespaceId(val value: String) {
    init { require(!value.contains(" ")) { "Идентификатор содержит пробелы" } }

    override fun toString(): String {
        return value
    }
}

/**
 * # Пространство имён
 * Логическая единица структуры Engine, содержащий в себе любой возможный контент - звуки, предметы, компоненты, действия и т.д.
 * Структура пространств имён игрока должна совпадать с структурой сервера для разрешения войти
 */
data class Namespace(
    val id: NamespaceId,
    val items: ContentHolder<ItemId, ItemPrefab> = ContentHolder(),
    val sounds: ContentHolder<SoundEventId, SoundEvent> = ContentHolder(),
    val progressionAnimations: ContentHolder<ProgressionAnimationId, ProgressionAnimation> = ContentHolder(),
    val scripts: ContentHolder<ScriptId, Script<*, *>> = ContentHolder(),
    val components: ContentHolder<ScriptComponentId, ScriptComponentType> = ContentHolder(),
    val operations: ContentHolder<OperationId, Operation> = ContentHolder(),
    val systems: ContentHolder<ScriptSystemId, ScriptSystem> = ContentHolder()
) {
    val holders = listOf(items, sounds, scripts, progressionAnimations, components, operations, systems)
    val hash by lazy { holders.hashCode() }
}
typealias NamespaceHashMap = Map<NamespaceId, Int>

interface NamespacedStorageAccess : Contents {
    fun get(): NamespacedStorage
    fun update(storage: NamespacedStorage)
}

class ThreadSafeNamespaceStorageAccessImpl(
    @Volatile var namespacedStorage: NamespacedStorage
) : NamespacedStorageAccess {
    override val operations: ContentHolder<OperationId, Operation>
        get() = namespacedStorage.operations
    override val components: ContentHolder<ScriptComponentId, ScriptComponentType>
        get() = namespacedStorage.components
    override val sounds: ContentHolder<SoundEventId, SoundEvent>
        get() = namespacedStorage.sounds
    override val progressionAnimations: ContentHolder<ProgressionAnimationId, ProgressionAnimation>
        get() = namespacedStorage.progressionAnimations
    override val scripts: ContentHolder<ScriptId, Script<*, *>>
        get() = namespacedStorage.scripts
    override val items: ContentHolder<ItemId, ItemPrefab>
        get() = namespacedStorage.items
    override val systems: ContentHolder<ScriptSystemId, ScriptSystem>
        get() = namespacedStorage.systems

    override fun get(): NamespacedStorage = namespacedStorage
    override fun update(storage: NamespacedStorage) {
        namespacedStorage = storage
    }
}
class NamespacedStorage(
    val namespaces: Map<NamespaceId, Namespace> = mapOf(),
    override val sounds: ContentHolder<SoundEventId, SoundEvent> = ContentHolder(),
    override val items: ContentHolder<ItemId, ItemPrefab> = ContentHolder(),
    override val progressionAnimations: ContentHolder<ProgressionAnimationId, ProgressionAnimation> = ContentHolder(),
    override val scripts: ContentHolder<ScriptId, Script<*, *>> = ContentHolder(),
    override val components: ContentHolder<ScriptComponentId, ScriptComponentType> = ContentHolder(),
    override val operations: ContentHolder<OperationId, Operation> = ContentHolder(),
    override val systems: ContentHolder<ScriptSystemId, ScriptSystem> = ContentHolder()
) : Contents {
    val namespaceHashMap: NamespaceHashMap = namespaces.map { (id, namespace) -> id to namespace.hash }.toMap()
}
