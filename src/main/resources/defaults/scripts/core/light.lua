require("core.world")
require("core.registration")

---@type World for EmmyLua
local World = World

------------------

---@class LightSourceBehaviour
---@field type string sphere, cone
---@field params any
LightSourceBehaviour = {}

---@field radius number blocks
---@return LightSourceBehaviour
function LightSourceBehaviour.sphere(radius)
    return {
        type = "sphere",
        params = {
            radius = radius
        }
    }
end

--- @class LightSourceComponent : Component
--- @field behaviour LightSourceBehaviour
--- Builtin
LightSourceComponent = Component.of("core/light/source")

---@field behaviour LightSourceBehaviour
---@return LightSourceComponent
function LightSourceComponent.new(behaviour)
    assert(behaviour ~= nil, "behaviour must be not null")
    return LightSourceComponent:construct({ behaviour = behaviour })
end

------------------

--- @class LuminanceComponent : Component
--- @field level number from 0 to 14
--- Builtin
LuminanceComponent = Component.of("core/light/luminance")

---@field level number from 0 to 14
---@return LuminanceComponent
function LuminanceComponent.new(level)
    assert(level ~= nil, "light level must be not null")
    return LuminanceComponent:construct({ level = level })
end

------------------

---@field behaviour LightSourceBehaviour
---@field level number from 0 to 14
---@field pos number[]
---@return Entity
function World:add_light_entity(behaviour, level, pos)
    local entity = self:add_entity()
    entity:set_light_entity(behaviour, level, pos, entity)
    return entity
end

---@field behaviour LightSourceBehaviour
---@field level number from 0 to 14
---@field pos number[]
function Entity:set_light_entity(behaviour, level, pos)
    self:set_component(LightSourceComponent.new(behaviour))
    self:set_component(LuminanceComponent.new(level))
    self:set_component(LocationComponent.vector(pos))
end

------------------

--- @class FlashingComponent : Component
--- @field min number from 0 to 14
--- @field max number from 0 to 14
--- @field pattern string Quake-like light animation pattern.
--- Each character represents a light from min level to max level, mapped from 'A' to 'O'.
--- Example: pattern "AOAOO" -> 0,14,0,14,14
--- @field index number
FlashingComponent = Component.of("core/light/flashing", { networking = true, savable = true })

---@field min number
---@field max number
---@field pattern string
---@return FlashingComponent
function FlashingComponent.new(pattern, min, max)
    assert(min < max, "max light level must be greater than min")
    assert(pattern:match("^[a-o]+$"), "pattern must contain only chars a-o")
    return FlashingComponent:construct({ min = min, max = max, pattern = pattern, index = 1 })
end

ticks = 0

---@param world World
---@param entity Entity
---@param flash FlashingComponent
---@param luminance LuminanceComponent
local function FlashSystem(world, entity, flash, luminance)
    local index = flash.index
    local char = flash.pattern:sub(index, index)
    local char_index = string.byte(char) - string.byte('a')
    local t = char_index / 14
    local level = flash.min + t * (flash.max - flash.min)
    luminance.level = level

    flash.index = flash.index + 1
    if flash.index > #flash.pattern then
        flash.index = 1
    end
end

Callbacks.build()
        :on_world_tick(function(context) if (context.is_client) then ticks = ticks + 1 end end)
        :system({ FlashingComponent, LuminanceComponent }, FlashSystem, "client")
        :submit()