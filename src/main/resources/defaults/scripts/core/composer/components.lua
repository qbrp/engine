local lazy = require("core.util.lazy")

---@class ComponentTypeDefinitionDraft : ComponentTypeSettings
---@field __is_definition true

---@class ComponentTypeDefinition<T> : ComponentTypeHolder<T>, ComponentTypeSettings
---@field __lazy_holder LazyComponentTypeHolder
local component_type_definition = {}
component_type_definition.__index = function (self, key)
    if key == "type" then
        return self.__lazy_holder.type
    end
end

local components = {}

---@param components table<Id, Symbol<ComponentTypeSettings|ComponentTypeDefinitionDraft>>
function components.setup_definitions(components)
    for id, symbol in pairs(components) do
        local value = symbol.value ---@type ComponentTypeSettings|ComponentTypeDefinitionDraft
        if value.__is_definition then
            ---@cast value ComponentTypeDefinition
            local type_holder = lazy.component_holder(id.full)
            value.__lazy_holder = type_holder
        end
    end
end

---@generic T : Component
---@param settings ComponentTypeSettings
---@return ComponentTypeDefinition<T>
function components.definition(settings)
    local settings_copy = table.shallow_copy(settings)
    ---@cast settings_copy ComponentTypeDefinitionDraft
    settings_copy.__is_definition = true
    return setmetatable(settings_copy, component_type_definition)
end

return components