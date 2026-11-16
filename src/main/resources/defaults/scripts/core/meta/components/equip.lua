---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.
--- 
---@class EquipmentComponent : Component
---@field slots MutableMap<Id, Entity>

---@class EquipmentSlot
---@field id Id
---@field name string
---@field part PlayerPart
---@field rotate_left_side boolean Поворачивать модель на 180 градусов, если находится в левой части тела игрока

---@class EquippableComponent : Component