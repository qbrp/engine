---Userdata
---@class ComponentType
---@field id string

--- @class Component
--- @field type_of fun(id: string): ComponentType static, kotlin
--- @field type ComponentType static
Component = Component

---@param class Component
---@param table table?
---@return Component
function Component.construct(class, table)
    local component = setmetatable(table or {}, class)
    return component
end

---@generic T : Component
---@param id string
---@return T
function Component.of(id)
    assert(id ~= nil, "id must be not null")
    local component_type = Component.type_of(id)
    local class = table or {}
    class.__index = class
    class.type = component_type
    return class
end
