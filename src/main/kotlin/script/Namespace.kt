package org.lain.engine.script

import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.player.ProgressionAnimation
import org.lain.engine.player.ProgressionAnimationId
import org.lain.engine.util.Intent
import org.lain.engine.util.IntentId
import org.lain.engine.util.NamespaceId
import org.lain.engine.world.SoundEvent
import org.lain.engine.world.SoundEventId

/**
 * # Пространство имён
 * Логическая единица структуры Engine, содержащий в себе любой возможный контент - звуки, предметы, компоненты, действия и т.д.
 * Структура пространств имён игрока должна совпадать с структурой сервера для разрешения войти
 */
data class Namespace(
    val id: NamespaceId,
    val items: Holder<ItemId, ItemPrefab>,
    val sounds: Holder<SoundEventId, SoundEvent>,
    val progressionAnimations: Holder<ProgressionAnimationId, ProgressionAnimation>,
    val scripts: Holder<ScriptId, Script<*, *>>,
    val components: Holder<ScriptComponentId, ScriptComponentType>,
    val intents: Holder<IntentId, Intent>
) {
    val holders = listOf(items, sounds, scripts, progressionAnimations, components, intents)
    val hash by lazy { holders.hashCode() }

    class Holder<K, V>(private val map: Map<K, V> = mapOf()) : Map<K, V> by map {
        val ids: List<String> by lazy { map.keys.map { it.toString() } }
        val idHash by lazy { ids.hashCode() }

        override fun hashCode(): Int = idHash
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Holder<*, *>
            if (map != other.map) return false
            if (idHash != other.idHash) return false
            return true
        }
    }
}

interface ContentStorage {
    val sounds: Namespace.Holder<SoundEventId, SoundEvent>
    val items: Namespace.Holder<ItemId, ItemPrefab>
    val progressionAnimations: Namespace.Holder<ProgressionAnimationId, ProgressionAnimation>
    val scripts: Namespace.Holder<ScriptId, Script<*, *>>
    val components: Namespace.Holder<ScriptComponentId, ScriptComponentType>
    val intents: Namespace.Holder<IntentId, Intent>
}


typealias NamespaceHashMap = Map<NamespaceId, Int>

class NamespacedStorage : ContentStorage {
    var namespaces: Map<NamespaceId, Namespace> = mapOf()
        private set
    override var sounds: Namespace.Holder<SoundEventId, SoundEvent> = Namespace.Holder()
    override var items: Namespace.Holder<ItemId, ItemPrefab> = Namespace.Holder()
    override var progressionAnimations: Namespace.Holder<ProgressionAnimationId, ProgressionAnimation> = Namespace.Holder()
    override var scripts: Namespace.Holder<ScriptId, Script<*, *>> = Namespace.Holder()
    override var components: Namespace.Holder<ScriptComponentId, ScriptComponentType> = Namespace.Holder()
    override var intents: Namespace.Holder<IntentId, Intent> = Namespace.Holder()

    @get:Synchronized
    @set:Synchronized
    var namespaceHashMap: NamespaceHashMap = hashMap()

    private fun hashMap() = namespaces.map { (id, namespace) -> id to namespace.hash }.toMap()

    fun upload(namespaces: List<Namespace>) {
        this.namespaces = namespaces.associateBy { it.id }
        items = collect { it.items }
        sounds = collect { it.sounds }
        progressionAnimations = collect { it.progressionAnimations }
        scripts = collect { it.scripts }
        components = collect { it.components }
        intents = collect { it.intents }
        namespaceHashMap = hashMap()
    }

    private fun <K, V> collect(property: (Namespace) -> Namespace.Holder<K, V>): Namespace.Holder<K, V> {
        val entries = mutableMapOf<K, V>()
        namespaces.forEach { (_, namespace) ->
            entries.putAll(property(namespace))
        }
        return Namespace.Holder(entries)
    }
}
