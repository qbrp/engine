require("core.bridge")
require("core.world")
require("core.component")

--------------------------------------------------------------------------------
---- Встроенные системы
--------------------------------------------------------------------------------

---@param narration Narration|string
---@param time number?
---@param kick boolean?
function Player:narration(narration, time, kick)
    local narration_table
    if getmetatable(narration) == Narration then
        narration_table = narration
    else
        narration_table = Narration.new(narration, time, kick)
    end
    self:__narration(narration_table)
end

------------------

---@param speed number
function Player:set_custom_max_speed(speed) self:__set_custom_max_speed(speed) end

function Player:reset_custom_max_speed() self:__reset_custom_max_speed() end

------------------

---@return boolean
function Player:is_game_master() self:__is_game_master() end

--------------------------------------------------------------------------------
---- Работа с компонентами
--------------------------------------------------------------------------------

---@param component Component
function Player:set_component(component)
    assert(component, "component must be not null")
    self.world:set_component(self.entity_id, component)
end

---@generic T : Component
---@param component Component|ComponentType
---@return T?
function Player:remove_component(component)
    assert(component, "component type must be not null")
    return self.world:remove_component(self.entity_id, component.type or component)
end

---@generic T : Component
---@param component Component|ComponentType
---@return T?
function Player:get_component(component)
    assert(component, "component type must be not null")
    return self.world:get_component(self.entity_id, component.type or component)
end

--------------------------------------------------------------------------------
---- Компонентные утилиты
--------------------------------------------------------------------------------

---@class PlayerComponent : Component
---@field object Player
PlayerComponent = Component.of("core/player/component")

---@param types ComponentType[]|Component[]
---@param fun fun(player: Player, ...)
function World:iterate_players(types, fun)
    table.insert(types, 1, PlayerComponent)
    self:iterate(types, function(entity, player, ...)
        fun(player.object, ...)
    end)
end


---@param types ComponentType[]|Component[]
---@param fun fun(world: World, player: Player, ...)
---@param env string client or server
---@return Callbacks
function Callbacks:player_system(types, fun, env)
    table.insert(types, PlayerComponent)
    return self:system(types, function(world, entity, player, ...) fun(world, player.object) end, env)
end

--------------------------------------------------------------------------------
---- Заморозка
--------------------------------------------------------------------------------

---@class FreezeComponent : Component
---@field duration number ticks
---@field time number ticks elapsed
local FreezeComponent = Component.of("core/player/freeze")

---@return FreezeComponent
---@field duration number
function FreezeComponent.new(duration) return FreezeComponent:construct { duration=duration, time=0 } end

---@param world World
---@param player Player
---@param freeze FreezeComponent
local function FreezeSystem(world, player, freeze)
    if (freeze.time > freeze.duration) then
        player:remove_component(FreezeComponent)
        player:reset_custom_max_speed()
        return
    end

    player:set_custom_max_speed(0)
    freeze.time = freeze.time + 1
end

---@param ticks number
function Player:freeze(ticks)
    self:remove_component(FreezeComponent)
    self:set_component(FreezeComponent.new(ticks))
end

--------------------------------------------------------------------------------
---- Инициализация
--------------------------------------------------------------------------------

Callbacks.build()
        :player_system({ FreezeComponent }, FreezeSystem)
        :submit()