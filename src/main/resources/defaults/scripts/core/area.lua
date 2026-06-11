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

---@param context IntentScriptContext
function RelativeTeleportationScript(context)
    local world = context.world
    if world.is_client then return end

    local from = world:get_area_map().areas[context.inputs.from]
    local to = world:get_area_map().areas[context.inputs.to]

    if not from then
        context.feedback("area " .. tostring(context.inputs.from) .. " not found")
        return
    end

    if not to then
        context.feedback("area " .. tostring(context.inputs.to) .. " not found")
        return
    end

    for _, player in ipairs(world.players) do
        local position = player.entity:get_component(LocationComponent).vector
        local inside = position[1] >= from.x1 and position[1] <= from.x2 and
            position[2] >= from.y1 and position[2] <= from.y2 and
            position[3] >= from.z1 and position[3] <= from.z2

        if inside then
            local relative_position = {
                position[1] - from.x1,
                position[2] - from.y1,
                position[3] - from.z1
            }

            local new_position = {
                to.x1 + relative_position[1],
                to.y1 + relative_position[2],
                to.z1 + relative_position[3]
            }

            info(player.invoke_command)
            player:invoke_command(
                "tp " ..
                    tostring(new_position[1]) .. " " ..
                    tostring(new_position[2]) .. " " ..
                    tostring(new_position[3]),
                true
            )
        end
    end
end