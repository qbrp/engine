require("core.bridge")
require("core.world")
require("core.component")
require("core.easing")

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

---@field sound string|table
---@field parameters AudioSource
---@return Entity, RepeatableComponent
function World:add_sound_entity(sound, parameters)
    assert(sound, "sound must be not null")
    assert(self.is_client, "world must be client")
    local entity = self:add_entity()
    parameters = parameters or {}
    if (not type(sound) == table) then
        parameters.sound = { id = sound }
    else
        parameters.sound = sound
    end
    local pos = parameters.pos
    if (pos ~= nil) then
        assert(type(pos) == "table", "pos parameter must be vector array")
        parameters.x = pos[1]
        parameters.y = pos[2]
        parameters.z = pos[3]
        parameters.is_relative = false
    end
    local sound_component = SoundComponent.new(parameters)
    entity:set_component(sound_component)
    sound_component.source:play()
    return entity, sound_component
end

------------------

---@class RepeatableComponent : Component
---@field repeats_left number
RepeatableComponent = Component.of("core/sound/repeatable")

---@param repeats number
---@return RepeatableComponent
function RepeatableComponent.new(repeats, eternal)
    return RepeatableComponent:construct({ repeats_left = repeats, eternal = eternal })
end

---@param sound SoundComponent
---@param repeatable RepeatableComponent
---@param world World
---@param entity Entity
local function RepeatableSystem(world, entity, sound, repeatable)
    local audio_source = sound.source
    if (audio_source.is_ended) then
        if (not repeatable.eternal) then
            repeatable.repeats_left = repeatable.repeats_left - 1
        end
        if (repeatable.repeats_left > 0) then
            local copied_entity = world:add_entity()
            for_each(entity:get_all_components(), function(component)
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

------------------

---@class PitchSlideComponent : Component
---@field pitch number
---@field easing Easing
---@field duration number in ticks
---@field progress number from 0 to duration
PitchSlideComponent = Component.of("core/sound/pitch_slide")

---@param pitch number
---@param duration number
---@param easing Easing?
---@return PitchSlideComponent
function PitchSlideComponent.new(pitch, duration, easing)
    return PitchSlideComponent:construct(
            {
                pitch = pitch,
                duration = duration,
                easing = easing or Easing.ease_in_sine,
                progress = 0
            }
    )
end

---@param entity Entity
---@param sound SoundComponent
---@param pitch_slide PitchSlideComponent
local function PitchSlideSystem(world, entity, sound, pitch_slide)
    local progress = pitch_slide.progress
    local duration = pitch_slide.duration
    local easing = pitch_slide.easing

    if progress > duration then
        entity:remove_component(PitchSlideComponent)
    else
        pitch_slide.progress = progress + 1
        local t = progress / duration
        local source = sound.source
        source.pitch = easing(t)
    end
end

------------------

---@class VoxelSoundComponent : Component
---@field voxel_pos number[3]
VoxelSoundComponent = Component.of("core/sound/voxel")

---@param voxel_pos number[3]
---@return VoxelSoundComponent
function VoxelSoundComponent.of(voxel_pos)
    return VoxelSoundComponent:construct({ voxel_pos = voxel_pos })
end

---@param world World
---@param voxel_sound VoxelSoundComponent
---@param sound SoundComponent
---@param entity Entity
local function VoxelSoundTrackSystem(world, entity, sound, voxel_sound)
    if (world:get_dynamic_voxel(voxel_sound.voxel_pos) == nil) then
        sound.source:stop()
        entity:destroy()
    end
end

------------------

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
        :system({ SoundComponent, PitchSlideComponent }, PitchSlideSystem, "client")
        :system({ SoundComponent, VoxelSoundComponent }, VoxelSoundTrackSystem, "client")
        :submit()