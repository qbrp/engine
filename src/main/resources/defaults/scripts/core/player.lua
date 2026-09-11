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
---@field narration_internal fun(self: Player, narration: Narration)
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

---@overload fun(narration: string, time?: number, kick: boolean?)
---@param narration Narration
---@param time? number
---@param kick? boolean
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

---@enum PlayerMode
PlayerMode = {
    DEFAULT = "default",
    GAME_MASTER = "game_master",
    SPECTATOR = "spectator"
}

---@class PlayerModeComponent : Component
---@field mode PlayerMode
---@field is_game_master boolean
---@field is_spectator boolean
---readonly
PlayerModeComponent = Component.of("core/player/game_mode")

---@class PlayerPhysicsComponent : Component
---@field no_clip boolean writable
PlayerPhysicsComponent = Component.of("core/player/physics")

---@class PlayerComponent : Component
---@field object Player
PlayerComponent = Component.of("core/player/component")

---@class PlayerInputComponent : Component
---@field is_sprinting boolean readonly
PlayerInputComponent = Component.of("core/player/input")

---@class PlayerAttributesComponent : Component
---@field speed number writable
---@field fly_speed number writable
---@field jump_strength number writable
---@field gravity number writable
PlayerAttributesComponent = Component.of("core/player/attributes")

---@class PlayerCustomAttributesComponent : Component
---@field speed number? writable
---@field jump_strength number? writable
---@field gravity number? writable
PlayerCustomAttributesComponent = Component.of("core/player/custom_attributes")

---@class MovementStatusComponent : Component
---@field intention number readonly, from 0 to 1
---@field stamina number writable, from 0 to 1
MovementStatusComponent = Component.of("core/player/movement_status")

---@class PlayerVelocityComponent : Component
---@field motion vec3 readonly
---@field previous vec3 readonly
PlayerVelocityComponent = Component.of("core/player/velocity")

---@class JumpComponent : Component
local JumpComponent = Component.of("core/player/jump")

--------------------------------------------------------------------------------
---- Движение
--------------------------------------------------------------------------------

---@class PlayerMovementPrimaryAttributes
---@field speed number
---@field jump_strength number

---@class PlayerMovementSettings
---@field sprint_multiplier number Множитель скорости в режиме бега
---@field min_speed_factor number Множитель к показателю аттрибута скорости, определяющий самую низкую возможную скорость (от 0 до 1)
---@field slowdown_stamina_threshold number Порог стамины, при котором персонаж начинает замедляться
---@field stamina_consumption number Уменьшение стамины за 1 тик при максимальной скорости
---@field stamina_regen number **Постоянное** восстановление стамины за 1 тик. Стамина тратиться, когда `stamina_consumption` становится больше `stamina_regen`
---@field intention_effect number Как сильно `intention` игрока влияет на скорость. Ограничивает множитель скорости.
---@field sprint_min_intention_effect number Минимальное значение `intention_effect`, когда игрок бежит (прибавляется к нему)
---@field jump_stamina_consume number Потребление стамины при прыжке
---@field primary_attributes table<PlayerMode, PlayerMovementPrimaryAttributes>
---@type PlayerMovementSettings
local movement_settings = {
    sprint_multiplier = 1.5,
    min_speed_factor = 0.05,
    slowdown_stamina_threshold = 0.3,
    stamina_consumption = 0.0033,
    stamina_regen = 0.003,
    sprint_min_intention_effect = 0.5,
    intention_effect = 0.7,
    jump_stamina_consume = 0.3,
    primary_attributes = {
        [PlayerMode.DEFAULT] = { speed = 0.055, jump_strength = 0.4 },
        [PlayerMode.GAME_MASTER] = { speed = 0.12, jump_strength = 0.45 },
        [PlayerMode.SPECTATOR] = { speed = 0.15, jump_strength = 0.55 }
    }
}

---@param value number
---@param min number
---@param max number
---@return number
local function clamp(value, min, max)
    return math.max(min, math.min(value, max))
end

---@param start number
---@param target number
---@param alpha number
---@return number
local function lerp(start, target, alpha)
    return start * (1 - alpha) + target * alpha
end

---@param value number
---@return number
local function smoothstep(value)
    return 3 * value * value - 2 * value * value * value
end

---@param value number
---@return number
local function smootherstep(value)
    return 6 * value ^ 5 - 15 * value ^ 4 + 10 * value ^ 3
end

---@param intention number
---@param stamina number
---@param is_sprinting boolean
---@param settings PlayerMovementSettings
---@return number
local function speed_multiplier(intention, stamina, is_sprinting, settings)
    local sprint_multiplier = 1
    local min_speed_addition = 0
    if is_sprinting then
        sprint_multiplier = settings.sprint_multiplier
        min_speed_addition = settings.sprint_min_intention_effect
    end

    local stamina_multiplier = 1
    if stamina < settings.slowdown_stamina_threshold then
        stamina_multiplier = smoothstep(stamina / settings.slowdown_stamina_threshold)
    end

    local min_intention_multiplier = 1 - settings.intention_effect + min_speed_addition
    local max_intention_multiplier = settings.intention_effect * 2 - 1
    local intention_multiplier = min_intention_multiplier + max_intention_multiplier * smootherstep(intention)
    return intention_multiplier * stamina_multiplier * sprint_multiplier
end

---@param mode PlayerModeComponent
---@return PlayerMovementPrimaryAttributes
local function get_primary_attributes(mode)
    return movement_settings.primary_attributes[mode.mode]
        or movement_settings.primary_attributes[PlayerMode.DEFAULT]
end

local StaminaConsumptionSystem = System(
    "core/player/stamina_consumption",
    { MovementStatusComponent, PlayerModeComponent, PlayerVelocityComponent },
    SystemSide.BOTH
)

---@param movement MovementStatusComponent
---@param mode PlayerModeComponent
---@param velocity PlayerVelocityComponent
function StaminaConsumptionSystem.update(world, entity, movement, mode, velocity)
    local jumped = entity:remove_component(JumpComponent) ~= nil
    if mode.is_spectator then
        return
    end

    if mode.is_game_master then
        movement.stamina = 1
        return
    end

    local primary_attributes = get_primary_attributes(mode)
    local max_speed = primary_attributes.speed * speed_multiplier(1, 1, true, movement_settings)
    local motion = velocity.motion
    local horizontal_speed = math.sqrt(motion.x * motion.x + motion.z * motion.z)
    local movement_consumption = 0
    if max_speed > 0 then
        movement_consumption = math.abs(horizontal_speed) / max_speed * movement_settings.stamina_consumption
    end
    local jump_consumption = jumped and movement_settings.jump_stamina_consume or 0

    movement.stamina = clamp(
        movement.stamina + movement_settings.stamina_regen - movement_consumption - jump_consumption,
        0,
        1
    )
end

local SpeedApplicationSystem = System(
    "core/player/speed_application",
    { MovementStatusComponent, PlayerModeComponent, PlayerInputComponent, PlayerAttributesComponent },
    SystemSide.BOTH
)

---@param movement MovementStatusComponent
---@param mode PlayerModeComponent
---@param input PlayerInputComponent
---@param attributes PlayerAttributesComponent
function SpeedApplicationSystem.update(world, entity, movement, mode, input, attributes)
    local primary_attributes = get_primary_attributes(mode)
    local default_speed = primary_attributes.speed

    if movement.stamina > movement_settings.jump_stamina_consume then
        attributes.jump_strength = primary_attributes.jump_strength
    else
        attributes.jump_strength = 0
    end

    if mode.is_spectator then
        attributes.speed = default_speed
        return
    end

    local max_speed = default_speed * speed_multiplier(1, 1, true, movement_settings)
    local min_speed = math.min(default_speed * movement_settings.min_speed_factor, max_speed)
    local target = clamp(
        default_speed * speed_multiplier(movement.intention, movement.stamina, input.is_sprinting, movement_settings),
        min_speed,
        max_speed
    )
    attributes.speed = lerp(attributes.speed, target, 0.2)
end

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

local FreezeSystem = System(
    "core/player/freeze",
    { PlayerComponent, PlayerCustomAttributesComponent, FreezeComponent }
)

---@param player_component PlayerComponent
---@param attributes PlayerCustomAttributesComponent
---@param freeze FreezeComponent
function FreezeSystem.update(world, entity, player_component, attributes, freeze)
    local player = player_component.object
    if (freeze.time > freeze.duration) then
        attributes.speed = nil
        player:remove_component(FreezeComponent)
        return
    end

    attributes.speed = 0
    freeze.time = freeze.time + 1
end

---@param ticks number
function Player:freeze(ticks)
    self:remove_component(FreezeComponent)
    self:set_component(FreezeComponent.new(ticks))
end

--------------------------------------------------------------------------------
--- NoClip
--------------------------------------------------------------------------------

local SpectatingNoClipSystem = System(
    "core/player/spectating_no_clip",
    { PlayerModeComponent, PlayerPhysicsComponent },
    SystemSide.BOTH
)

---@param mode PlayerModeComponent
---@param physics PlayerPhysicsComponent
function SpectatingNoClipSystem.update(world, entity, mode, physics)
    if mode.is_spectator then
        physics.no_clip = true
    else
        physics.no_clip = false
    end
end

---@param context IntentScriptContext
function NoClipScript(context)
    local player = context.actor.player
    if player ~= nil then
        local physics = player:get_component(PlayerPhysicsComponent)
        physics.no_clip = not physics.no_clip
    end
end

local no_clip = Script.new("noclip", NoClipScript)

--------------------------------------------------------------------------------
--- Инициализация
--------------------------------------------------------------------------------

function CompilationResult:setup_player()
    self:namespace {
        id = "core/player",
        components = ComponentList { "freeze" },
        scripts = { no_clip },
        intents = {
            Intent.of(
                "noclip",
                "core/player/noclip",
                true,
                "Переключить режим NoClip"
            )
        },
        systems = {
            StaminaConsumptionSystem,
            SpeedApplicationSystem,
            FreezeSystem,
            SpectatingNoClipSystem
        }
    }
    self:phase(
        "player",
        {
            StaminaConsumptionSystem,
            SpeedApplicationSystem,
            SpectatingNoClipSystem,
            FreezeSystem
        }
    )
end
