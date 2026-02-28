package org.lain.engine.util

import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.player.ProgressionAnimation
import org.lain.engine.player.ProgressionAnimationId
import org.lain.engine.world.SoundEvent
import org.lain.engine.world.SoundEventId

/**
 * # Пространство имён
 * Логическая единица структуры Engine, содержащий в себе любой возможный контент - звуки, предметы и действия.
 * Структура пространств имён игрока должна совпадать с структурой сервера для разрешения войти
 */
data class Namespace(
    val id: NamespaceId,
    val items: Holder<ItemId, ItemPrefab>,
    val sounds: Holder<SoundEventId, SoundEvent>,
    val progressionAnimations: Holder<ProgressionAnimationId, ProgressionAnimation>,
) {
    class Holder<K, V>(private val map: Map<K, V> = mapOf()) : Map<K, V> by map {
        val ids: List<String> by lazy { map.keys.map { it.toString() } }
    }
}

interface ContentStorage {
    val sounds: Namespace.Holder<SoundEventId, SoundEvent>
    val items: Namespace.Holder<ItemId, ItemPrefab>
    val progressionAnimations: Namespace.Holder<ProgressionAnimationId, ProgressionAnimation>
}

class NamespacedStorage : ContentStorage {
    var namespaces: Map<NamespaceId, Namespace> = mapOf()
        private set
    override var sounds: Namespace.Holder<SoundEventId, SoundEvent> = Namespace.Holder()
    override var items: Namespace.Holder<ItemId, ItemPrefab> = Namespace.Holder()
    override var progressionAnimations: Namespace.Holder<ProgressionAnimationId, ProgressionAnimation> = Namespace.Holder()

    fun upload(namespaces: List<Namespace>) {
        this.namespaces = namespaces.associateBy { it.id }
        items = collect { it.items }
        sounds = collect { it.sounds }
        progressionAnimations = collect { it.progressionAnimations }
    }

    private fun <K, V> collect(property: (Namespace) -> Namespace.Holder<K, V>): Namespace.Holder<K, V> {
        val entries = mutableMapOf<K, V>()
        namespaces.forEach { (_, namespace) ->
            entries.putAll(property(namespace))
        }
        return Namespace.Holder(entries)
    }
}
