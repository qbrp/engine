package org.lain.engine.script.lua.compilation

import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.ItemAssets
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.ItemSounds
import org.lain.engine.script.*
import org.lain.engine.script.compilation.NamespaceDraft
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.library.luaWritableEntity
import org.lain.engine.script.lua.library.resolveIdReference
import org.lain.engine.script.lua.nullable
import org.lain.engine.script.lua.toMap
import org.lain.engine.script.lua.toStringMap
import org.lain.engine.world.SoundEventId
import org.luaj.vm2.LuaTable

context(lua: LuaScriptEngine)
fun compileItemPrefabsLua(namespaceId: NamespaceId, items: List<LuaTable>): List<ItemPrefab> =
    items.map { item ->
        val itemId = item.get("id").resolveIdReference()
        val displayName = item.get("display_name").tojstring()
        val maxCount = item.get("max_count").toint()
        val assets = item.get("assets").nullable()?.checktable()?.toMap { it } ?: mapOf()
        val soundEvents = item.get("sound_events").nullable()?.checktable()
            ?.toMap { SoundEventId(EngineId(it.tojstring())) }
        val onLoad = item.get("on_load").checkfunction()

        ItemPrefab(
            ItemId(itemId),
            maxCount,
            displayName,
            ItemAssets(
                assets.mapValues { (key, value) -> value.resolveIdReference() }
            ),
            null,
            { entity ->
                soundEvents?.let { entity.setComponent(ItemSounds(it)) }
                onLoad.call(entity.luaWritableEntity())
            }
        )
    }