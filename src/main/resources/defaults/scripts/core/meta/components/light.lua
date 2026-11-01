---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class LightBehaviour
---@field type "sphere"
---@field params LightBehaviourParams

---@class LightBehaviourParams
---@field radius integer

---@class LightSourceComponent : Component
---@field behaviour LightBehaviour

---@class LuminanceComponent : Component
---@field level integer