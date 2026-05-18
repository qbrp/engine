package org.lain.engine.script

import org.lain.engine.item.ItemAssets
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.ItemTooltip
import org.lain.engine.player.ProgressionAnimation
import org.lain.engine.player.ProgressionAnimationId
import org.lain.engine.script.Namespace.Holder
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
    val items: Holder<ItemId, ItemPrefab> = Holder(),
    val sounds: Holder<SoundEventId, SoundEvent> = Holder(),
    val progressionAnimations: Holder<ProgressionAnimationId, ProgressionAnimation> = Holder(),
    val scripts: Holder<ScriptId, Script<*, *>> = Holder(),
    val components: Holder<ScriptComponentId, ScriptComponentType> = Holder(),
    val intents: Holder<IntentId, Intent> = Holder()
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

interface NamespacedStorageAccess : ContentStorage {
    fun get(): NamespacedStorage
    fun update(storage: NamespacedStorage)
}

class ThreadSafeNamespaceStorageAccessImpl(
    @Volatile var namespacedStorage: NamespacedStorage
) : NamespacedStorageAccess {
    override val intents: Holder<IntentId, Intent>
        get() = namespacedStorage.intents
    override val components: Holder<ScriptComponentId, ScriptComponentType>
        get() = namespacedStorage.components
    override val sounds: Holder<SoundEventId, SoundEvent>
        get() = namespacedStorage.sounds
    override val progressionAnimations: Holder<ProgressionAnimationId, ProgressionAnimation>
        get() = namespacedStorage.progressionAnimations
    override val scripts: Holder<ScriptId, Script<*, *>>
        get() = namespacedStorage.scripts
    override val items: Holder<ItemId, ItemPrefab>
        get() = namespacedStorage.items

    override fun get(): NamespacedStorage = namespacedStorage
    override fun update(storage: NamespacedStorage) {
        namespacedStorage = storage
    }
}

fun emptyNamespacedStorage() = NamespacedStorage(emptyList())

fun namespacedStorageWithBuiltins(namespaces: List<Namespace>): NamespacedStorage {
    val namespacesWithBuiltins = namespaces.toMutableList()
    val coreErrorNamespaceId = NamespaceId("core/error")
    val invalidItem = ItemPrefab(
        ItemId(INVALID_ITEM_ID), 64,
        "Недействительный предмет",
        ItemAssets.withDefaultAsset(INVALID_ITEM_ID),
        null,
        {
            ItemTooltip(INVALID_ITEM_TOOLTIPS.random())
        }
    )
    val coreErrorNamespaceItems = mapOf(ItemId("core/error/item") to invalidItem)
    namespacesWithBuiltins += Namespace(
        coreErrorNamespaceId,
        Holder(coreErrorNamespaceItems)
    )
    return NamespacedStorage(namespacesWithBuiltins)
}

class NamespacedStorage internal constructor(namespacesList: List<Namespace>) : ContentStorage {
    val namespaces: Map<NamespaceId, Namespace> = namespacesList.associateBy { it.id }.toMutableMap()

    override val sounds: Namespace.Holder<SoundEventId, SoundEvent> = collect { it.sounds }
    override val items: Namespace.Holder<ItemId, ItemPrefab> = collect { it.items }
    override val progressionAnimations: Namespace.Holder<ProgressionAnimationId, ProgressionAnimation> = collect { it.progressionAnimations }
    override val scripts: Namespace.Holder<ScriptId, Script<*, *>> = collect { it.scripts }
    override val components: Namespace.Holder<ScriptComponentId, ScriptComponentType> = collect { it.components }
    override val intents: Namespace.Holder<IntentId, Intent> = collect { it.intents }

    val namespaceHashMap: NamespaceHashMap = namespaces.map { (id, namespace) -> id to namespace.hash }.toMap()

    private fun <K, V> collect(property: (Namespace) -> Namespace.Holder<K, V>): Namespace.Holder<K, V> {
        val entries = mutableMapOf<K, V>()
        namespaces.forEach { (_, namespace) ->
            entries.putAll(property(namespace))
        }
        return Namespace.Holder(entries)
    }
}

val INVALID_ITEM_ID = "core/error/item"

private val INVALID_ITEM_TOOLTIPS = listOf(
    "Помните, обилие багов - симптом активной разработки<newline>(C) lain1wakura",
    "i'm psyho",
    "если бы все мужчины были гомосексуальны, немецкий народ исчез бы,<newline>но если бы все женщины были лесбиянками, «они бы все равно рожали детей»",
    "Господи, храни америку!"
)