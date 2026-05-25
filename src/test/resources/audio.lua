require("core.bridge")
require("core.world")
require("core.component")

---@type World for EmmyLua
local World = World

---@type AudioSource for EmmyLua
local AudioSource = AudioSource

--------------------------------------------------------------------------------
---- Генерация идентификаторов
--------------------------------------------------------------------------------

local last_generated_id = 0

---@return number
local function generate_slot_id()
    last_generated_id = last_generated_id + 1
    return last_generated_id
end


--------------------------------------------------------------------------------
---- Источники
--------------------------------------------------------------------------------

---@param parameters AudioSource
---@return AudioSource
function AudioSource.create(parameters)
    assert(parameters, "parameters must be not null")
    assert(parameters.sound, "sound must be not null")
    return AudioSource.__create(parameters)
end

---@param slot string?
---@return string
function AudioSource:play(slot)
    local source2 = slot or "audio_slot_" .. generate_slot_id()
    self:__play(source2)
    return source2
end

function AudioSource:stop() self:__stop() end


--------------------------------------------------------------------------------
---- Звуки-сущности
--------------------------------------------------------------------------------

---@class SoundComponent : Component
---@field source AudioSource
SoundComponent = Component.of("core/sound/component")

---@return SoundComponent
function SoundComponent.new(parameters)
    return SoundComponent:construct({ source = AudioSource.create(parameters) })
end

function SoundComponent:play()
    self.slot = self.source:play()
end

---@field sound string
---@field parameters AudioSource
---@return Entity
function World:add_sound_entity(sound, parameters)
    assert(sound, "sound must be not null")
    assert(self.is_client, "world must be client")
    local entity = self:add_entity()
    local parameters2 = parameters or {}
    parameters2.sound = sound
    local pos = parameters2.pos
    if (pos ~= nil) then
        assert(type(pos) == "table", "pos parameter must be vector array")
        parameters2.x = pos[1]
        parameters2.y = pos[2]
        parameters2.z = pos[3]
        parameters2.is_relative = false
    end
    local sound_component = SoundComponent.new(parameters2)
    entity:set_component(sound_component)
    sound_component.source:play()
    return entity
end

------------------

---@class RepeatableComponent : Component
---@field repeats_left number
RepeatableComponent = Component.of("core/sound/repeatable")

---@param repeats number
---@return RepeatableComponent
function RepeatableComponent.new(repeats) return RepeatableComponent:construct({ repeats_left = repeats }) end

---@param sound SoundComponent
---@param repeatable RepeatableComponent
---@param world World
---@param entity Entity
local function RepeatableSystem(world, entity, sound, repeatable)
    local audio_source = sound.source
    if (audio_source.is_ended) then
        repeatable.repeats_left = repeatable.repeats_left - 1
        if (repeatable.repeats_left > 0) then
            local copied_entity = world:add_entity()
            for_each(entity:get_all(), function(component)
                if (component.type ~= SoundComponent.type) then
                    copied_entity:set_component(component)
                end
            end)
            copied_entity:set_component(SoundComponent:construct { source = sound.source })
            sound.source:play()
            entity:destroy()
        end
    end
end

---@param sound SoundComponent
---@param world World
---@param entity Entity
local function PlaybackSystem(world, entity, sound)
    if (sound.source.is_ended) then
        entity:destroy()
    end
end

Callbacks.build()
        :system({ SoundComponent, RepeatableComponent }, RepeatableSystem, "client")
        :system({ SoundComponent }, PlaybackSystem, "client")
        :submit()