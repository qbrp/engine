---@class GunComponentDraft
---@field smoke number[]?
---@field rate integer
---@field modes GunFireMode[]

---@alias GunFireMode "SAFETY"|"SINGLE"|"AUTO"

---@class GunFireStateComponentDraft
---@field mode GunFireMode

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

---@class ItemNameComponent : Component
---@field text string

---@class AssetVariants : Component
---@field assets table<string, Id>
---@field default Id? геттер, берётся из assets["default"]

---@class ItemComponent : Component
---@field uuid string
---@field prefab_id Id
