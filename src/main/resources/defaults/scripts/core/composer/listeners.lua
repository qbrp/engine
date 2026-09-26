local listeners = {}

---@param listeners_list EventListeners[]
---@param categorized_declarations CategorizedDeclarations
function listeners.collect(listeners_list, categorized_declarations)
    for category, declarations in pairs(categorized_declarations) do
        if category.id == "listeners" then
            for _, declaration in ipairs(declarations) do
                table.insert(listeners_list, declaration.value)
            end
        end
    end
end

---@param listeners_list EventListeners[]
---@return EventListeners
function listeners.merge(listeners_list)
    local result = {}

    local function order_function(name)
        local functions = {}
        
        local handlers = {
            show_item_tooltip = function (context)
                local lines = {}
                for _, fun in ipairs(functions) do
                    local result = fun(context)
                    assert(type(result) == "string", "event callback is not string")
                    table.insert(lines, result)
                end
                return lines
            end,
            default = function (context)
                for _, fun in ipairs(functions) do
                    fun(context)
                end
            end
        }

        for _, listeners in ipairs(listeners_list) do
            local fun = listeners[name]
            if fun then
                table.insert(functions, fun)
            end
        end

        result[name] = handlers[name] or handlers.default
    end

    order_function("player_instantiate")
    order_function("player_destroy")
    order_function("player_input_tick")
    order_function("world_tick")
    order_function("world_tick_20")
    order_function("place_voxel")
    order_function("item_load")
    order_function("show_item_tooltip")

    return result
end

return listeners
