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

---@class GunComponentDraft
---@field smoke number[]?
---@field rate integer
---@field modes GunFireMode[]

---@alias GunFireMode "SAFETY"|"SINGLE"|"AUTO"

---@class GunFireStateComponentDraft
---@field cooldown integer
---@field mode GunFireMode
---@field clicked boolean
---@field trigger_pressed boolean
---@field trigger_sound_played boolean
---@field fired boolean

---@class BarrelComponentDraft
---@field bullets integer
---@field max_bullets integer
---@field ammunition IdReference?

---@class GunMagazinesComponentDraft
---@field supports IdReference

---@class GunDisplayComponentDraft
---@field ammunition string?
---@field magazine string?
---@field selector_status boolean

---@class MagazineComponentDraft
---@field capacity integer
---@field bullets integer
---@field ammunition IdReference
