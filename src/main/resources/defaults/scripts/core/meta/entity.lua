---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class Entity : ComponentState
---@field world World
---@field id integer
---@field readable boolean
entity = {}

---@class WriteOnlyEntity : Entity
---@field readable false

---@return boolean
function entity:exists() end

---@param type ComponentTypeHolder
function entity:mark_updated(type) end

function entity:destroy() end