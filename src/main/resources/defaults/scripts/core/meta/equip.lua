---@class EquipmentComponent : Component
---@field slots MutableMap<Id, Entity>

---@class EquippableComponent : Component

---@class EquipmentSlot
---@field id Id
---@field name string
---@field part PlayerPart
---@field rotate_left_side boolean Поворачивать модель на 180 градусов, если находится в левой части тела игрока