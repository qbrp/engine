require("core.component")
require("core.world")

---@type World for EmmyLua
local World = World

---@class Area
---@field x1 number
---@field x2 number
---@field y1 number
---@field y2 number
---@field z1 number
---@field z2 number
Area = Area or {}

---@param pos1 number[] voxel pos
---@param pos2 number[] voxel pos
---@return Area
function Area.rectangle(pos1, pos2)
    return setmetatable(
            {
                x1 = pos1[1], y1 = pos1[2], z1 = pos1[3],
                x2 = pos2[1], y2 = pos2[2], z2 = pos2[3],
            },
            Area
    )
end

---@class AreaState
---@field players Player[]

---@class AreaMapComponent : Component
---@field areas table<string, Area>
AreaMapComponent = Component.of("core/area/map_persistent")

---@return AreaMapComponent
function AreaMapComponent.new()
    return AreaMapComponent:construct({ areas = {} })
end

---@class AreaStateComponent : Component
---@field states table<string, AreaState>
AreaStateComponent = Component.of("core/area/map_state")

---@return AreaStateComponent
function AreaStateComponent.new()
    return AreaStateComponent:construct({ states = {} })
end

---@return AreaMapComponent
function World:get_area_map()
    return self.state:get_or_create_component(AreaMapComponent, function() return AreaMapComponent.new() end)
end

---@return AreaStateComponent
function World:get_state_map()
    return self.state:get_or_create_component(AreaStateComponent, function() return AreaStateComponent.new() end)
end

---@param context IntentScriptContext
function CreateAreaScript(context)
    local world = context.world
    if (world.is_client) then return end
    local selection = context.gen_selection()
    local id = context.inputs.id
    if (selection == nil) then
        context.feedback("selection is null, use worldedit commands //pos1 //pos2 or wooden axe")
        return
    end
    local area = Area.rectangle(selection.pos1, selection.pos2)
    local areas = world:get_area_map().areas
    local states = world:get_state_map().states
    areas[id] = area
    states[id] = { players = {} }
    context.feedback("created area " .. id)
end

---@param context IntentScriptContext
function ListAreasScript(context)
    local world = context.world
    if (world.is_client) then return end
    local areas = world:get_area_map().areas
    local areas_formatted = {}
    for id, _ in pairs(areas) do
        table.insert(areas_formatted, id)
    end
    context.feedback(table.concat(areas_formatted, ", "))
end