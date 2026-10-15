---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class ComponentLibrary
component_library = {}

---@class ComponentTypeHolder<T>
---@field access LuaComponentAccess<T>?
---@field id Id

---@class LuaComponentAccess<T>

---@class ComponentBinding<T> : LuaComponentAccess<T>
---@field id Id

---@class ComponentType<T> : LuaComponentAccess<T>
---@field id Id
component_type = {}

---@alias ComponentTypeReference IdReference|ComponentType

---@generic T : Component
---@param id ComponentTypeReference
---@return ComponentType<T>? type
---@return boolean ready  завершено ли разрешение типа
function component_library.get_type(id) end

---@generic T : Component
---@param id ComponentTypeReference
---@return ComponentBinding<T>? binding
---@return boolean ready  завершено ли разрешение типа
function component_library.get_binding(id) end

---@class Component : any
component = {}