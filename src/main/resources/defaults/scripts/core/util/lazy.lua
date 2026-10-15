local lazy = {}

---@class LazyComponentTypeHolder<T> : ComponentTypeHolder<T>
---@field id Id
---@field get_type fun(id: Id): LuaComponentAccess<T>
---@field computed LuaComponentAccess<T>?
local lazy_component_type_holder = {}
lazy_component_type_holder.__index = function (self, key)
    if key == "access" then
        local computed = self.computed
        if not computed then
            local type, ready = self.get_type(self.id)
            if not ready then
                error("модуль components импортирован до завершения компиляции")
            end 
            computed = type
        end
        return computed
    end
end

---@generic T : Component
---@param id IdLiteral
---@param get_type (fun(id: Id): LuaComponentAccess<T>)?
---@return LazyComponentTypeHolder<T>
function lazy.component_holder(id, get_type)
    local parsed_id = engine.id.parse(id)
    return setmetatable({ 
        id = parsed_id,
        get_type = get_type or engine.component.get_type,
        __id_reference = parsed_id,
    }, lazy_component_type_holder)
end

return lazy