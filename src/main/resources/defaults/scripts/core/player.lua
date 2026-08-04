require("core.bridge")
require("core.world")
require("core.component")

---@class Player : Entity
---@field id string
---@field uuid string
---@field entity Entity
---@field world World
---@field is_game_master boolean
---@field is_spectating boolean
---@field invoke_command fun(self: Player, command: string, root: boolean)
---@field has_permission fun(self: Player, permission: string): boolean
---@field set_flying_speed fun(self: Player, speed: number): boolean
---@field set_custom_max_speed fun(self: Player, speed: number)
---@field reset_custom_speed fun(self: Player)
---@field narration_internal fun(narration: Narration)
---@field set_action_component fun(self: Player, component: Component)
Player = Player

--------------------------------------------------------------------------------
---- Взаимодействия
--------------------------------------------------------------------------------

---@class InputAction
---@field type "base, attack, take_off"

---@class PlayerInputTickScriptContext
---@field player Player
---@field actions InputAction[]
---@field last_actions InputAction[]
---@field is_spectating boolean
---@field social_interaction_distance number
---@field extend_arm boolean

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


---@class PlayerInventoryComponent : Component
---@field main_hand_item Entity
---@field off_hand_item Entity
---@field items Entity[]
---@field selected_slot number int
---readonly
PlayerInventoryComponent = Component.of("core/player/inventory")

---@class PlayerComponent : Component
---@field object Player
PlayerComponent = Component.of("core/player/component")

--------------------------------------------------------------------------------
---- Заморозка
--------------------------------------------------------------------------------

---@class FreezeComponent : Component
---@field duration number ticks
---@field time number ticks elapsed
local FreezeComponent = Component.of("core/player/freeze")

---@return FreezeComponent
---@param duration number
function FreezeComponent.new(duration) return FreezeComponent:construct { duration=duration, time=0 } end

local FreezeSystem = System("freeze", { PlayerComponent, FreezeComponent })

---@param player_component PlayerComponent
---@param freeze FreezeComponent
function FreezeSystem.update(world, entity, player_component, freeze)
    local player = player_component.object
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

function CompilationResult:setup_player()
    self:namespace {
        id = "core/player",
        components = ComponentList { "freeze" },
        systems = { FreezeSystem }
    }
    self:phase("player", { FreezeSystem })
end
