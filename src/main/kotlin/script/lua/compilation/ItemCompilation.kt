package org.lain.engine.script.lua.compilation

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.copyState
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.Barrel
import org.lain.engine.item.FireMode
import org.lain.engine.item.Gun
import org.lain.engine.item.GunDisplay
import org.lain.engine.item.GunFireState
import org.lain.engine.item.GunMagazines
import org.lain.engine.item.ItemAssets
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.ItemSounds
import org.lain.engine.item.Magazine
import org.lain.engine.script.EngineId
import org.lain.engine.script.NamespaceId
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.library.luaWritableEntity
import org.lain.engine.script.lua.library.resolveIdReference
import org.lain.engine.script.lua.nullable
import org.lain.engine.script.lua.toList
import org.lain.engine.script.lua.toMap
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.SoundEventId
import org.lain.engine.world.toSoundEventId
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

private typealias ComponentFactory = () -> Component

private fun LuaValue.toFireMode(): FireMode = FireMode.valueOf(checkjstring())

private fun LuaValue.toSmokeOffset() = checktable().toList { it.checkdouble().toFloat() }.let { coordinates ->
    require(coordinates.size == 3) { "Gun smoke offset must contain exactly 3 coordinates" }
    Vec3(coordinates[0], coordinates[1], coordinates[2])
}

private fun LuaTable.toGunComponentFactory(): ComponentFactory {
    val smoke = get("smoke").nullable()?.toSmokeOffset()
    val rate = get("rate").checkint()
    val modes = get("modes").checktable().toList { it.toFireMode() }
    return { Gun(smoke, rate, modes) }
}

private fun LuaTable.toGunFireStateComponentFactory(): ComponentFactory {
    val cooldown = get("cooldown").checkint()
    val mode = get("mode").toFireMode()
    val clicked = get("clicked").checkboolean()
    val triggerPressed = get("trigger_pressed").checkboolean()
    val triggerSoundPlayed = get("trigger_sound_played").checkboolean()
    val fired = get("fired").checkboolean()
    return { GunFireState(cooldown, mode, clicked, triggerPressed, triggerSoundPlayed, fired) }
}

private fun LuaTable.toBarrelComponentFactory(): ComponentFactory {
    val bullets = get("bullets").checkint()
    val maxBullets = get("max_bullets").checkint()
    val ammunition = get("ammunition").nullable()
        ?.let { ItemId(it.resolveIdReference()) }
    return { Barrel(bullets, maxBullets, ammunition) }
}

private fun LuaTable.toGunMagazinesComponentFactory(): ComponentFactory {
    val supports = ItemId(get("supports").resolveIdReference())
    return { GunMagazines(supports) }
}

private fun LuaTable.toGunDisplayComponentFactory(): ComponentFactory {
    val ammunition = get("ammunition").nullable()?.checkjstring()
    val magazine = get("magazine").nullable()?.checkjstring()
    val selectorStatus = get("selector_status").checkboolean()
    return { GunDisplay(ammunition, magazine, selectorStatus) }
}

private fun LuaTable.toMagazineComponentFactory(): ComponentFactory {
    val capacity = get("capacity").checkint()
    val bullets = get("bullets").checkint()
    val ammunition = ItemId(get("ammunition").resolveIdReference())
    return { Magazine(capacity, bullets, ammunition) }
}

private fun LuaTable.toBuiltInComponentFactories(): List<ComponentFactory> = buildList {
    get("gun").nullable()?.checktable()?.let { add(it.toGunComponentFactory()) }
    get("gun_fire_state").nullable()?.checktable()?.let { add(it.toGunFireStateComponentFactory()) }
    get("barrel").nullable()?.checktable()?.let { add(it.toBarrelComponentFactory()) }
    get("gun_magazines").nullable()?.checktable()?.let { add(it.toGunMagazinesComponentFactory()) }
    get("gun_display").nullable()?.checktable()?.let { add(it.toGunDisplayComponentFactory()) }
    get("magazine").nullable()?.checktable()?.let { add(it.toMagazineComponentFactory()) }
}

context(lua: LuaScriptEngine)
fun compileItemPrefabsLua(namespaceId: NamespaceId, items: List<LuaTable>): List<ItemPrefab> =
    items.map { item ->
        val itemId = item.get("id").resolveIdReference()
        val displayName = item.get("display_name").tojstring()
        val maxCount = item.get("max_count").toint()
        val assets = item.get("assets").nullable()?.checktable()?.toMap { it } ?: mapOf()
        val soundEvents = item.get("sound_events").nullable()?.checktable()
            ?.toMap { it.resolveIdReference().toSoundEventId() }
        val builtInComponentFactories = item.get("built_in_components").nullable()?.checktable()
            ?.toBuiltInComponentFactories()
            ?: emptyList()
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
                entity.copyState(builtInComponentFactories.map { it() })
                soundEvents?.let { entity.setComponent(ItemSounds(it)) }
                onLoad.call(entity.luaWritableEntity())
            }
        )
    }
