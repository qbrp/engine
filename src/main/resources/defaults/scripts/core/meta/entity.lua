---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class EntityReference
---@field id integer

---@class Entity
---@field world World
---@field id integer
---@field readable boolean
entity = {}

---@class WriteOnlyEntity : Entity
---@field readable false

---@return EntityReference
function entity:reference() end

---@return boolean
function entity:exists() end

---@generic T : Component
---@param type ComponentTypeHolder<T>
---@return T?
function entity:get_component(type) end

---@param type ComponentTypeHolder
---@return boolean
function entity:has_component(type) end

---@param type ComponentTypeHolder
---@param component table
function entity:set_component(type, component) end

---@generic T : Component
---@param type ComponentTypeHolder<T>
---@return T?
function entity:remove_component(type) end

---@param type ComponentTypeHolder
function entity:mark_updated(type) end

---@param ignore_query boolean вывести ли все компоненты
---@return table<ComponentType, table>
function entity:list_components(ignore_query) end

function entity:destroy() end