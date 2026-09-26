---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class ComponentLibrary
component_library = {}

---@generic T : Component
---@class ComponentTypeHolder
---@field type ComponentType<T>

---@generic T : Component
---@class ComponentType
---@field id Id
component_type = {}

---@alias ComponentTypeReference IdReference|ComponentType

---@generic T : Component
---@param id ComponentTypeReference
---@return ComponentType<T>? type
---@return boolean ready  завершено ли разрешение типа
function component_library.get_type(id) end

---@generic T : Component
---@param type ComponentTypeReference
---@return ComponentTypeHolder<T>? type
---@return boolean ready  завершено ли разрешение типа
function component_library.get_holder(type) end

---@class Component
component = {}