local lazy = require("core.util.lazy")

---@class ComponentTypeDefinitionDraft<T> : ComponentTypeSettings
---@field __is_definition true
---@field __original ComponentTypeDefinitionDraft<T>

---@class ComponentTypeDefinition<T> : ComponentTypeHolder<T>, ComponentTypeSettings
---@field __lazy_holder LazyComponentTypeHolder
---@field __id_reference Id
local component_type_definition = {}
component_type_definition.__index = function (self, key)
    if key == "access" then
        return self.__lazy_holder.access
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
            value.id = id
            value.__lazy_holder = type_holder
            value.__id_reference = id
        end
    end
end

---@generic T : Component
---@param settings ComponentTypeSettings
---@return ComponentTypeDefinition<T>
function components.definition(settings)
    ---@cast settings ComponentTypeDefinitionDraft
    settings.__is_definition = true
    local settings_copy = table.shallow_copy(settings)
    return setmetatable(settings_copy, component_type_definition)
end

return components