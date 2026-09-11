package org.lain.engine.script

import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.player.interaction.ProgressionAnimation
import org.lain.engine.player.interaction.ProgressionAnimationId
import org.lain.engine.script.compilation.NamespaceDraft
import org.lain.engine.util.Operation
import org.lain.engine.util.OperationId
import org.lain.engine.world.SoundEvent
import org.lain.engine.world.SoundEventId
import kotlin.collections.component1
import kotlin.collections.component2

interface Identifiable {
    val engineId: EngineId
}

interface Contents {
    val sounds: ContentHolder<SoundEventId, SoundEvent>
        get() = ContentHolder.empty()
    val items: ContentHolder<ItemId, ItemPrefab>
        get() = ContentHolder.empty()
    val progressionAnimations: ContentHolder<ProgressionAnimationId, ProgressionAnimation>
        get() = ContentHolder.empty()
    val scripts: ContentHolder<ScriptId, Script<*, *>>
        get() = ContentHolder.empty()
    val components: ContentHolder<ScriptComponentId, ScriptComponentType>
        get() = ContentHolder.empty()
    val operations: ContentHolder<OperationId, Operation>
        get() = ContentHolder.empty()
    val systems: ContentHolder<ScriptSystemId, ScriptSystem>
        get() = ContentHolder.empty()
}

class ContentHolder<K : Identifiable, V>(
    private val map: Map<K, V> = mapOf()
) : Map<K, V> by map {

    val ids: Collection<K> get() = map.keys
    val stringIds get() = ids.map { it.engineId.toString() }
    val idHash get() = ids.hashCode()

    override fun hashCode(): Int = idHash
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ContentHolder<*, *>
        if (map != other.map) return false
        if (idHash != other.idHash) return false
        return true
    }

    companion object {
        fun <K : Identifiable, V> empty() = ContentHolder<K, V>(mutableMapOf())
    }
}

fun <K  : Identifiable, V> Map<NamespaceId, NamespaceDraft>.collect(property: (NamespaceDraft) -> Map<K, V>): Map<K, V> {
    val entries = mutableMapOf<K, V>()
    forEach { (_, namespace) ->
        entries.putAll(property(namespace))
    }
    return entries
}

fun <K : Identifiable, V> Map<NamespaceId, Namespace>.collect(property: (Namespace) -> Map<K, V>): ContentHolder<K, V> {
    val entries = mutableMapOf<K, V>()
    forEach { (_, namespace) ->
        entries.putAll(property(namespace))
    }
    return ContentHolder(entries)
}
