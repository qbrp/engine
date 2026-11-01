---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.
--- 
---@class ItemPrefabDraft
---@field id IdReference
---@field max_count integer
---@field assets table<string, IdReference>
---@field sound_events table<string, Id>?
---@field built_in_components ItemBuiltInComponents
---@field on_load fun(world: World, item: WriteOnlyEntity)

---@class ItemBuiltInComponents
---@field gun GunComponentDraft?
---@field gun_fire_state GunFireStateComponentDraft?
---@field barrel BarrelComponentDraft?
---@field gun_magazines GunMagazinesComponentDraft?
---@field gun_display GunDisplayComponentDraft?
---@field magazine MagazineComponentDraft?