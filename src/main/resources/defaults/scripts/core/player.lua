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
PlayerComponent = Component.builtin("core/player")

---@param types ComponentType[]|Component[]
---@param fun fun(player: Player, ...)
function World:iterate_players(types, fun)
    table.insert(types, 1, PlayerComponent)
    self:iterate(types, function(entity, player, ...)
        fun(player.object, ...)
    end)
end

--------------------------------------------------------------------------------
---- Прочие компоненты
--------------------------------------------------------------------------------

---@class LocationComponent : Component
---@field vector number[]
LocationComponent = Component.builtin("core/location")

--------------------------------------------------------------------------------
---- Заморозка
--------------------------------------------------------------------------------

---@class FreezeComponent : Component
---@field duration number ticks
---@field time number ticks elapsed
FreezeComponent = Component.create("core/freeze")

---@return FreezeComponent
---@field duration number
function FreezeComponent.new(duration) return FreezeComponent:construct { duration=duration, time=0 } end

---@param world World
function tick_freeze_system(world)
    world:iterate_players({ FreezeComponent }, function(player, freeze)
        if (freeze.time > freeze.duration) then
            player:remove_component(FreezeComponent)
            player:reset_custom_max_speed()
            return
        end

        player:set_custom_max_speed(0)
        freeze.time = freeze.time + 1
    end)
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
        :on_world_tick(tick_freeze_system)

compilation(function()
    local result = CompilationResult.new()
    result:namespace {
        id = "core",
        components = {
            FreezeComponent.type,
            PlayerComponent.type,
            LocationComponent.type,
            DynamicVoxelComponent.type,
            UseRestrictionComponent.type,
            SoundComponent.type,
            RepeatableComponent.type
        }
    }
    return result
end)