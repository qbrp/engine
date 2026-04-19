package org.lain.engine.script.lua

import org.lain.engine.player.ProgressionAnimationId
import org.lain.engine.script.*
import org.lain.engine.script.yaml.namespacedId
import org.lain.engine.util.NamespaceId
import org.lain.engine.world.SoundEventId
import org.luaj.vm2.LuaTable

fun compileItemsLua(namespaceId: NamespaceId, items: List<LuaTable>): List<CompiledNamespace.Item> = items.map { item ->
    val itemId = namespacedId(namespaceId, item.get("id").tojstring())
    val displayName = item.get("display_name").tojstring()
    val stackSize = item.get("stack_size").nullable()?.toint()
    val mass = item.get("mass").nullable()?.tofloat()
    val tooltip = item.get("tooltip").nullable()?.tojstring()
    val writable = item.get("writable").nullable()?.checktable()?.let {
        WritableConfig(
            it.get("pages").toint(),
            it.get("texture")?.nullable()?.tojstring(),
        )
    }
    val flashlight = item.get("flashlight").nullable()?.checktable()?.let {
        FlashlightConfig(
            it.get("radius").nullable()?.tofloat() ?: 8f,
            it.get("distance").nullable()?.tofloat() ?: 16f,
            it.get("light").nullable()?.tofloat() ?: 15f,
        )
    }

    val assets = item.get("assets").nullable()?.checktable()?.toStringMap()
    val progressionAnimations = item.get("progression_animations").nullable()?.checktable()
        ?.toMap { ProgressionAnimationId(it.tojstring()) }
    val soundEvents = item.get("sound_events").nullable()?.checktable()
        ?.toMap { SoundEventId(it.tojstring()) }

    CompiledItem(
        namespaceId,
        itemId,
        displayName,
        assets,
        progressionAnimations,
        soundEvents,
        stackSize,
        mass,
        tooltip,
        writable,
        flashlight,
    ).also { LOGGER.debug("Загружен предмет {}", itemId) }
}