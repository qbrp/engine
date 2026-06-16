--- Userdata
---@class ComponentType
---@field id string

--- @class Component
--- @field type_of fun(id: string): ComponentType static, kotlin
--- @field type ComponentType static
Component = Component
Component.__index = Component

---@param class ComponentType
---@param table table?
---@return Component
function Component.construct(class, table)
    local component = setmetatable(table or {}, class)
    return component
end

---@generic T : Component
---@param id string|ComponentType
---@param table T?
---@return T
function Component.of(id, table)
    assert(id ~= nil, "id must be not null")
    local component_type = id
    if (type(id) == "string") then
        component_type = Component.type_of(id)
    end
    local class = table or {}
    setmetatable(class, { __index = Component })
    class.__index = class
    class.type = component_type
    return class
end
