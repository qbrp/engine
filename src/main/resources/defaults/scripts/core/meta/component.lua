---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class ComponentLibrary
---@field component_state ComponentState метатаблица
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

--- конструктор
---@return ComponentState
function component_library.state() end

---@class Component : any
component = {}


--- Хранилище данных на основе компонентов
---@class ComponentState
component_state = {}

---@generic T : Component
---@param type ComponentTypeHolder<T>
---@return T?
function component_state:get_component(type) end

---@generic T : Component
---@param type ComponentTypeHolder<T>
---@return T
function component_state:require_component(type) end

---@param type ComponentTypeHolder
---@return boolean
function component_state:has_component(type) end

---@generic T : Component
---@param type ComponentTypeHolder<T>
---@param component T
---@return T
function component_state:set_component(type, component) end

---@generic T : Component
---@param type ComponentTypeHolder<T>
---@return T?
function component_state:remove_component(type) end

---@param ignore_query boolean вывести ли все компоненты
---@return table<ComponentType, Component>
function component_state:list_components(ignore_query) end