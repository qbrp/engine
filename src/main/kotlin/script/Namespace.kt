package org.lain.engine.script

import org.lain.engine.item.ItemAssets
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.ItemTooltip
import org.lain.engine.player.interaction.ProgressionAnimation
import org.lain.engine.player.interaction.ProgressionAnimationId
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
    val items: ContentHolder<ItemId, ItemPrefab> = ContentHolder(),
    val sounds: ContentHolder<SoundEventId, SoundEvent> = ContentHolder(),
    val progressionAnimations: ContentHolder<ProgressionAnimationId, ProgressionAnimation> = ContentHolder(),
    val scripts: ContentHolder<ScriptId, Script<*, *>> = ContentHolder(),
    val components: ContentHolder<ScriptComponentId, ScriptComponentType> = ContentHolder(),
    val intents: ContentHolder<IntentId, Intent> = ContentHolder(),
    val systems: ContentHolder<ScriptSystemId, ScriptSystemDefinition> = ContentHolder()
) {
    val holders = listOf(items, sounds, scripts, progressionAnimations, components, intents)
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
    override val intents: ContentHolder<IntentId, Intent>
        get() = namespacedStorage.intents
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
    override val systems: ContentHolder<ScriptSystemId, ScriptSystemDefinition>
        get() = namespacedStorage.systems

    override fun get(): NamespacedStorage = namespacedStorage
    override fun update(storage: NamespacedStorage) {
        namespacedStorage = storage
    }
}

object CoreNamespaces {
    val ERROR = Namespace(
        NamespaceId("core/error"),
        ContentHolder(
            mapOf(
                ItemId("core/error/item") to ItemPrefab(
                    ItemId(INVALID_ITEM_ID), 64,
                    "Недействительный предмет",
                    ItemAssets.withDefaultAsset(INVALID_ITEM_ID),
                    null,
                    {
                        ItemTooltip(INVALID_ITEM_TOOLTIPS.random())
                    }
                )
            )
        )
    )
}

class NamespacedStorage(
    val namespaces: Map<NamespaceId, Namespace> = mapOf(),
    override val sounds: ContentHolder<SoundEventId, SoundEvent> = ContentHolder(),
    override val items: ContentHolder<ItemId, ItemPrefab> = ContentHolder(),
    override val progressionAnimations: ContentHolder<ProgressionAnimationId, ProgressionAnimation> = ContentHolder(),
    override val scripts: ContentHolder<ScriptId, Script<*, *>> = ContentHolder(),
    override val components: ContentHolder<ScriptComponentId, ScriptComponentType> = ContentHolder(),
    override val intents: ContentHolder<IntentId, Intent> = ContentHolder(),
    override val systems: ContentHolder<ScriptSystemId, ScriptSystemDefinition> = ContentHolder()
) : Contents {
    val namespaceHashMap: NamespaceHashMap = namespaces.map { (id, namespace) -> id to namespace.hash }.toMap()
}

val INVALID_ITEM_ID = "core/error/item"

private val INVALID_ITEM_TOOLTIPS = listOf(
    "Помните, обилие багов - симптом активной разработки<newline>(C) lain1wakura",
    "i'm psyho",
    "если бы все мужчины были гомосексуальны, немецкий народ исчез бы,<newline>но если бы все женщины были лесбиянками, «они бы все равно рожали детей»",
    "Господи, храни америку!"
)