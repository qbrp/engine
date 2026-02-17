package org.lain.engine.util

import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.SoundEvent
import org.lain.engine.item.SoundEventId

/**
 * # Пространство имён
 * Логическая единица структуры Engine, содержащий в себе любой возможный контент - звуки, предметы и действия.
 * Структура пространств имён игрока должна совпадать с структурой сервера для разрешения войти
 */
data class Namespace(
    val id: NamespaceId,
    val items: Map<ItemId, ItemPrefab>,
    val sounds: Map<SoundEventId, SoundEvent>
) {
    val itemIdentifiers: List<String>
        get() = items.map { it.key.toString() }
    val soundIdentifiers: List<String>
        get() = sounds.map { it.key.toString() }
}

class NamespacedStorage {
    var sounds: Map<SoundEventId, SoundEvent> = emptyMap()
        private set
    var items: Map<ItemId, ItemPrefab> = mapOf()
        private set
    var namespaces: Map<NamespaceId, Namespace> = mapOf()
        private set
    var itemIdentifiers: List<String> = listOf()
        private set
    var soundIdentifiers: List<String> = listOf()
        private set

    fun upload(namespaces: List<Namespace>) {
        val itemIdentifiers2 = mutableListOf<String>()
        val soundIdentifiers2 = mutableListOf<String>()
        val namespaces2 = mutableMapOf<NamespaceId, Namespace>()
        val items = mutableMapOf<ItemId, ItemPrefab>()
        val sounds = mutableMapOf<SoundEventId, SoundEvent>()

        for (namespace in namespaces) {
            namespaces2[namespace.id] = namespace
            items.putAll(namespace.items)
            sounds.putAll(namespace.sounds)
            itemIdentifiers2 += namespace.itemIdentifiers
            itemIdentifiers2 += namespace.id.toString()
            soundIdentifiers2 += namespace.soundIdentifiers
        }

        this.itemIdentifiers = itemIdentifiers2
        this.soundIdentifiers = soundIdentifiers2
        this.namespaces = namespaces2
        this.items = items
        this.sounds = sounds
    }
}
