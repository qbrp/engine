require("core.bridge")

------------------

--- @class Component
--- @field type ComponentType static
--- @field constructor fun(...): Component static
Component = {}
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
        component_type = component_type_of(id)
    end
    local class = table or {}
    setmetatable(class, Component)
    class.__index = class
    class.type = component_type
    return class
end