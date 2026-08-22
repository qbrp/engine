---@diagnostic disable: unnecessary-assert
require("core.world")
require("core.component")
require("core.tween")

---@class Sound
---@field id string
---@field stream boolean false default

---Userdata
---@class AudioSource
---@field sound string|Sound
---@field category string
---@field x number
---@field y number
---@field z number
---@field is_relative boolean true default
---@field volume number from 0 to 1, default 1
---@field pitch number from 0 to 1, default 1
---@field attenuate boolean false default
---@field is_ended boolean
---@field radius number default 16
---@field play fun(self: AudioSource)
---@field stop fun(self: AudioSource)
---@field create fun(parameters: AudioSource): AudioSource
AudioSource = AudioSource

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

---@overload fun(self: World, sound: string, parameters?: AudioSource): Entity, SoundComponent
---@param sound Sound
---@param parameters? AudioSource
---@return Entity, SoundComponent
function World:add_sound_entity(sound, parameters)
    assert(sound, "sound must be not null")
    assert(self.is_client, "world must be client")
    local entity = self:add_entity()
    parameters = parameters or empty_table()
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
---@field eternal boolean
RepeatableComponent = Component.of("core/sound/repeatable")

---@param repeats number
---@param eternal boolean
---@return RepeatableComponent
function RepeatableComponent.new(repeats, eternal)
    return RepeatableComponent:construct({ repeats_left = repeats, eternal = eternal })
end

local RepeatSystem = System(
    "core/sound/repeats",
    { SoundComponent, RepeatableComponent },
    SystemSide.CLIENT
)

---@param sound SoundComponent
---@param repeatable RepeatableComponent
function RepeatSystem.update(world, entity, sound, repeatable)
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
                copied_entity:set_component(SoundComponent:construct { source = audio_source })
                audio_source:play()
                entity:destroy()
            end
        end
end

------------------

---@return Tween
function Tween.audio(value, start, final, duration, easing)
    ---@type fun(entity: Entity, p: number)
    local apply = function(entity, p)
        entity:get_component(SoundComponent).source[value] = p
    end
    return Tween.create(start, final, duration, apply, easing)
end

---@return Tween
function Tween.pitch(start, final, duration, easing)
    return Tween.audio("pitch", start, final, duration, easing)
end

---@return Tween
function Tween.volume(start, final, duration, easing)
    return Tween.audio("volume", start, final, duration, easing)
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

local VoxelSoundTrackSystem = System("core/sound/voxel_sound_tracking", { SoundComponent, VoxelSoundComponent }, SystemSide.CLIENT)

---@param voxel_sound VoxelSoundComponent
---@param sound SoundComponent
function VoxelSoundTrackSystem.update(world, entity, sound, voxel_sound)
    if (world:get_dynamic_voxel(voxel_sound.voxel_pos) == nil) then
        sound.source:stop()
        entity:destroy()
    end
end

------------------

local PlaybackSystem = System("core/sound/playback", { SoundComponent }, SystemSide.CLIENT)

---@param sound SoundComponent
function PlaybackSystem.update(world, entity, sound)
    if (sound.source.is_ended) then
        entity:destroy()
    end
end

function CompilationResult:setup_audio()
    self:namespace {
        id = "core/sound",
        components = ComponentList { "component", "repeatable", "voxel" },
        systems = { PlaybackSystem, VoxelSoundTrackSystem, RepeatSystem }
    }
    self:phase("audio", {
        VoxelSoundTrackSystem,
        RepeatSystem,
        PlaybackSystem,
    })
end