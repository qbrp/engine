package org.lain.engine.script.lua.library.ecs

import org.lain.engine.data.Uuid
import org.lain.engine.item.Item
import org.lain.engine.item.ItemAssets
import org.lain.engine.item.ItemName
import org.lain.engine.item.toItemPrefabId
import org.lain.engine.player.DecrementItem
import org.lain.engine.player.Equippable
import org.lain.engine.player.GiveItemEvent
import org.lain.engine.player.OutfitDisplay
import org.lain.engine.script.EngineId
import org.lain.engine.script.lua.*
import org.lain.engine.script.lua.library.ComponentBinding
import org.lain.engine.script.lua.library.asEngineId
import org.lain.engine.script.lua.library.asEnginePlayer
import org.lain.engine.script.lua.library.checkLuaEntity
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.script.lua.library.luaId
import org.lain.engine.script.lua.library.luaEntity

fun ItemNameBinding(cache: Boolean = false): ComponentBinding<ItemName> {
    val userdataType = LuaUserdataType<ItemName> {
        indexSelf { self, key ->
            when (key.tojstring()) {
                "text" -> self.text.luaStr()
                else -> NIL
            }
        }
    }

    return ComponentBinding(
        id = EngineId("core/item/name"),
        componentClass = ItemName::class,
        cache = cache,
        toLuaValue = { _, _, component -> userdataType.newInstance(component) },
        fromLuaValue = { _, value -> ItemName(value["text"].tojstring()) },
    )
}

fun AssetVariantsBinding(cache: Boolean = false) = ComponentBinding(
    id = EngineId("core/asset_variants"),
    componentClass = ItemAssets::class,
    cache = cache,
    toLuaValue = { _, _, component ->
        luaTable {
            "default"(component.default?.luaId())
            "assets"(
                component.assets.toLuaTable(
                    keyTransform = { it.luaStr() },
                    valueTransform = { it.luaId() }
                )
            )
        }
    },
    fromLuaValue = { _, value ->
        ItemAssets(
            value["assets"].checktable().toMap(
                valueTransform = { it.asEngineId() }
            )
        )
    },
)

fun EquippableBinding(cache: Boolean = false) = ComponentBinding(
    id = EngineId("core/item/equippable"),
    componentClass = Equippable::class,
    cache = cache,
    toLuaValue = { _, _, _ -> luaValue(true) },
    fromLuaValue = { _, _ -> Equippable(OutfitDisplay.Separated) },
)

fun DecrementItemBinding(cache: Boolean = false) = ComponentBinding(
    id = EngineId("core/item/decrement"),
    componentClass = DecrementItem::class,
    cache = cache,
    toLuaValue = { _, _, component -> luaValue(component.count) },
    fromLuaValue = { _, value -> DecrementItem(value.toint()) },
)

fun GiveItemBinding(cache: Boolean = false) = ComponentBinding(
    id = EngineId("core/player/inventory/give"),
    componentClass = GiveItemEvent::class,
    cache = cache,
    toLuaValue = { world, _, component ->
        with(world ?: error("world not available")) {
            luaTable {
                "player"(component.target.coerceToLua())
                "item"(component.item.luaEntity())
            }
        }
    },
    fromLuaValue = { _, value -> GiveItemEvent(value["player"].asEnginePlayer(), value["item"].checkLuaEntity()) },
)

fun ItemBinding(cache: Boolean = false) = ComponentBinding(
    id = EngineId("core/item"),
    componentClass = Item::class,
    cache = cache,
    toLuaValue = { _, _, component ->
        luaTable {
            "uuid"(component.uuid.toString())
            "prefab_id"(component.id.engineId.luaId())
        }
    },
    fromLuaValue = { _, value ->
        Item(
            Uuid.from(value["uuid"].tojstring()),
            value["prefab_id"].asEngineId().toItemPrefabId()
        )
    },
)
