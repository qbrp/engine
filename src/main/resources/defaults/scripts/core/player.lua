require("core.bridge")
require("core.world")
require("core.component")

---@class Player : Entity
---@field id string
---@field uuid string
---@field entity_id number
---@field world World
---@field is_game_master boolean
---@field is_spectating boolean
---@field invoke_command fun(self: Player, command: string, root: boolean)
---@field has_permission fun(self: Player, permission: string): boolean
---@field set_flying_speed fun(self: Player, speed: number): boolean
---@field set_custom_max_speed fun(self: Player, speed: number)
---@field reset_custom_speed fun(self: Player)
---@field narration_internal fun(narration: Narration)
Player = Player

--------------------------------------------------------------------------------
---- Встроенные системы
--------------------------------------------------------------------------------

---@class Narration
---@field message string minimessage
---@field time number ticks
---@field kick boolean false
Narration = {}
Narration.__index = Narration

---@return Narration
function Narration.new(message, time, kick)
    assert(message, "message must be not null")
    assert(time, "time must be not null")
    local narration = setmetatable({}, Narration)
    narration.message = message
    narration.time = time
    narration.kick = kick or false
    return narration
end

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
    self:narration_internal(narration_table)
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