---@class LazyComponentTypeHolder : ComponentTypeHolder
---@field id Id

local lazy = {}

local lazy_component_type_holder = {}
lazy_component_type_holder.__index = function (self, key)
    if key == "type" then
        local computed = self.computed
        if not computed then
            local type, ready = engine.component.get_type(self.id)
            if not ready then
                error("модуль components импортирован до завершения компиляции")
            end 
            computed = type
        end
        return computed
    end
end

---@param id IdLiteral
---@return LazyComponentTypeHolder
function lazy.component_holder(id)
    local parsed_id = engine.id.parse(id)
    return setmetatable({ 
        id = parsed_id,
        __id_reference = parsed_id,
    }, lazy_component_type_holder)
end

return lazy