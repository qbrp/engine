package org.lain.engine.script.lua.library.ecs

import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.EntityId
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
import org.lain.engine.script.lua.library.LuaComponentBinding
import org.lain.engine.script.lua.library.asEngineId
import org.lain.engine.script.lua.library.asEnginePlayer
import org.lain.engine.script.lua.library.checkLuaEntity
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.script.lua.library.luaEntity
import org.lain.engine.util.ecs.componentType
import org.lain.engine.world.World
import org.luaj.vm2.LuaValue

object ItemNameBinding : LuaComponentBinding<ItemName> {
    override val componentType: ComponentType<ItemName>
        get() = componentType<ItemName>()
    override val id: EngineId
        get() = EngineId("core/item/name")

    private val userdataType = LuaUserdataType<ItemName> {
        indexSelf { self, key ->
            when (key.tojstring()) {
                "text" -> self.text.luaStr()
                else -> NIL
            }
        }
    }

    context(lua: LuaScriptEngine)
    override fun toLua(
        world: World?,
        entity: EntityId,
        component: ItemName
    ): LuaValue {
        return userdataType.newInstance(component)
    }

    context(lua: LuaScriptEngine)
    override fun fromLua(entity: EntityId, value: LuaValue): ItemName {
        return ItemName(value["text"].tojstring())
    }
}

object AssetVariantsBinding : LuaComponentBinding<ItemAssets> {
    override val componentType: ComponentType<ItemAssets>
        get() = componentType<ItemAssets>()
    override val id: EngineId
        get() = EngineId("core/asset_variants")

    context(lua: LuaScriptEngine)
    override fun toLua(world: World?, entity: EntityId, component: ItemAssets): LuaValue {
        return luaTable {
            "default"(component.default?.coerceToLua())
            "assets"(
                component.assets.toLuaTable(
                    keyTransform = { it.luaStr() },
                    valueTransform = { it.coerceToLua() }
                )
            )
        }
    }

    context(lua: LuaScriptEngine)
    override fun fromLua(entity: EntityId, value: LuaValue): ItemAssets {
        return ItemAssets(
            value["assets"].checktable().toMap(
                valueTransform = { it.asEngineId() }
            )
        )
    }
}

object EquippableBinding : LuaComponentBinding<Equippable> {
    override val componentType: ComponentType<Equippable>
        get() = componentType<Equippable>()
    override val id: EngineId
        get() = EngineId("core/item/equippable")

    context(lua: LuaScriptEngine)
    override fun toLua(
        world: World?,
        entity: EntityId,
        component: Equippable
    ): LuaValue {
        return luaValue(true)
    }

    context(lua: LuaScriptEngine)
    override fun fromLua(entity: EntityId, value: LuaValue): Equippable {
        return Equippable(OutfitDisplay.Separated)
    }
}

object DecrementItemBinding : LuaComponentBinding<DecrementItem> {
    override val componentType: ComponentType<DecrementItem>
        get() = componentType<DecrementItem>()
    override val id: EngineId
        get() = EngineId("core/item/decrement")

    context(lua: LuaScriptEngine)
    override fun toLua(
        world: World?,
        entity: EntityId,
        component: DecrementItem
    ): LuaValue {
        return luaValue(component.count)
    }

    context(lua: LuaScriptEngine)
    override fun fromLua(entity: EntityId, value: LuaValue): DecrementItem {
        return DecrementItem(value.toint())
    }
}

object GiveItemBinding : LuaComponentBinding<GiveItemEvent> {
    override val componentType: ComponentType<GiveItemEvent>
        get() = componentType<GiveItemEvent>()
    override val id: EngineId
        get() = EngineId("core/player/inventory/give")

    context(lua: LuaScriptEngine)
    override fun toLua(
        world: World?,
        entity: EntityId,
        component: GiveItemEvent
    ): LuaValue {
        return with(world ?: error("world not available")) {
            luaTable {
                "player"(component.target.coerceToLua())
                "item"(component.item.luaEntity())
            }
        }
    }

    context(lua: LuaScriptEngine)
    override fun fromLua(entity: EntityId, value: LuaValue): GiveItemEvent {
        return GiveItemEvent(value["player"].asEnginePlayer(), value["item"].checkLuaEntity())
    }
}

object ItemBinding : LuaComponentBinding<Item> {
    override val componentType: ComponentType<Item>
        get() = componentType<Item>()
    override val id: EngineId
        get() = EngineId("core/item")

    context(lua: LuaScriptEngine)
    override fun toLua(
        world: World?,
        entity: EntityId,
        component: Item
    ): LuaValue {
        return luaTable {
            "uuid"(component.uuid.toString())
            "prefab_id"(component.id.engineId.coerceToLua())
        }
    }

    context(lua: LuaScriptEngine)
    override fun fromLua(entity: EntityId, value: LuaValue): Item {
        return Item(
            Uuid.from(value["uuid"].tojstring()),
            value["prefab_id"].asEngineId().toItemPrefabId()
        )
    }
}