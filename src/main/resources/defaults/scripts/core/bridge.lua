--------------------------------------------------------------------------------
---- Утилиты
--------------------------------------------------------------------------------

--- Логировать строку с уровнем INFO
---@param str string
function info(str) _info(str) end

function debug(module, str) _debug(module, str) end

function is_client() return _is_client() end

--- Сохранить значение на всю игровую сессию
---@param default
---@param slot string
---@param module string
function remember(default, slot, module) return _remember(default, slot, module or "global") end

--------------------------------------------------------------------------------
---- События
--------------------------------------------------------------------------------

---@class Callbacks
---@field player_instantiate fun(context: Player)
---@field player_destroy fun(context: Player)
---@field world_tick_20 fun(context: World)
---@field world_tick fun(context: World)
---@field place_voxel fun(context: VoxelActionScriptContext)
Callbacks = {}
Callbacks.__index = Callbacks

---@param arg fun(): Callbacks | Callbacks
function callbacks(arg)
    if type(arg) == "function" then
        _callbacks(arg)
    else
        _callbacks(function()
            return arg
        end)
    end
end

--------------------------------------------------------------------------------
---- Игрок
--------------------------------------------------------------------------------

-----------------

--- Userdata
---@class Player
---@field id string
---@field entity_id number
---@field world World
Player = Player or {}

-----------------

---@param speed number
function Player:__set_custom_max_speed(speed) self:_set_custom_max_speed(speed) end

function Player:__reset_custom_max_speed() self:_reset_custom_max_speed() end

---@return boolean
function Player:__is_game_master() self:_is_game_master() end

------------------

---@class Narration
---@field message string minimessage
---@field time number ticks
---@field kick boolean false
Narration = {}
Narration.__index = Narration

---@return Narration
function Narration.new(message, time, kick)
    assert(message, "message must be not null")
    assert(time, "message must be not null")
    local narration = setmetatable({}, Narration)
    narration.message = message
    narration.time = time
    narration.kick = kick or false
    return narration
end

---@param narration Narration
function Player:__narration(narration) self:_narration(narration) end

--------------------------------------------------------------------------------
---- Компоненты
--------------------------------------------------------------------------------

--- Userdata
---@class ComponentType
---@field id string

---@param id string
---@return ComponentType
function component_type_of(id) return _component_type_of(id) end

------------------

---@class World
---@field id string
---@field is_client boolean
World = World or {}

---@param type ComponentType
---@param entity_id number
---@return boolean
function World:__has_component(entity_id, type) return self:_has_component(entity_id, type) end

---@param type ComponentType
---@param entity_id number
---@return table
function World:__get_component(entity_id, type) return self:_get_component(entity_id, type) end

---@param type ComponentType
---@param entity_id number
---@return table?
function World:__remove_component(entity_id, type) return self:_remove_component(entity_id, type) end

---@param component
---@param type ComponentType
---@param entity_id number
function World:__set_component(entity_id, type, component) return self:_set_component(entity_id, type, component) end

---@param fun fun(...)
function World:__iterate(fun, ...) return self:_iterate(fun, ...) end

---@return number
function World:__add_entity() return self:_add_entity() end

---@param entity number
---@return Component[]
function World:__get_all_components(entity) return self:_get_all_components(entity) end

---@param entity number
function World:__destroy_entity(entity) return self:_destroy_entity(entity) end

------------------

--- Userdata
---@class Entity
---@field world World
---@field id number
Entity = Entity or {}
Entity.__index = Entity

---@param world World
---@param id number
---@return Entity
function Entity.__of(world, id) return Entity._of(world, id) end

--------------------------------------------------------------------------------
---- Воксели
--------------------------------------------------------------------------------

--- userdata
---@class VoxelMeta
---@field id string
---@field has_tag fun(tag: string): boolean

---@param voxel_pos number[]
---@param networked boolean
---@return number Entity
function World:__set_dynamic_voxel(voxel_pos, networked) return self:_set_dynamic_voxel(voxel_pos, networked) end

--------------------------------------------------------------------------------
---- Реестры
--------------------------------------------------------------------------------

-- Предмет

---@class Item
---@field id string
---@field display_name string
---@field assets table<string, string>
---@field stack_size number 1-64
---@field mass number kg
---@field tooltip string minimessage
---@field writable Writable
---@field flashlight Flashlight
---@field progression_animations table<string, string>
---@field sound_events table<string, string>

---@class Flashlight
---@field radius number meters
---@field distance number meters
---@field light number 0-15

---@class Writable
---@field pages number
---@field texture string id

------------------

---@class Script
---@field id string
---@field fun fun(context)
---@see InteractionScriptContext
---@see VoxelActionScriptContext
Script = Script or {}
Script.__index = Script

------------------

---@class IntentInput
---@field id string
---@field type string "text", "int", "double", "logic", "table" available
IntentInput = IntentInput or {}
IntentInput.__index = IntentInput

---@field id string
---@field type string
---@return IntentInput
function IntentInput.of(id, type)
    return setmetatable({ id = id, type = type }, IntentInput)
end

---@class Intent
---@field id string
---@field name string
---@field script string id
---@field inputs IntentInput[]
---@field actors string[] "command", "toolgun" available, default all
Intent = Intent or {}
Intent.__index = Intent

------------------

---@class ComponentTypeSettings
---@field id string
---@field savable string
---@field networking string
---
------------------

---@class Namespace
---@field id string
---@field items Item[]? empty default
---@field scripts Script[]? empty default
---@field components ComponentTypeSettings[]? empty default
---@field intents Intent[]? empty default
Namespace = Namespace or {}
Namespace.__index = Namespace

------------------

---@class CompilationResult
---@field namespaces Namespace[]
CompilationResult = {}
CompilationResult.__index = CompilationResult

function CompilationResult.new(namespaces)
    local obj = setmetatable({}, CompilationResult)
    obj.namespaces = namespaces or {}   -- поле для конкретного объекта
    return obj
end

------------------

---@param func fun(): CompilationResult
function compilation(func) _compilation(func) end

--------------------------------------------------------------------------------
---- Аудио
--------------------------------------------------------------------------------

--- Userdata
---@class  AudioSource
---@field sound string
---@field category string
---@field x number
---@field y number
---@field z number
---@field is_relative boolean true default
---@field volume number from 0 to 1
---@field pitch number from 0 to 1
---@field attenuate boolean false default
---@field is_ended boolean
AudioSource = AudioSource or {}

---@param parameters AudioSource
---@return AudioSource
function AudioSource.__create(parameters) return AudioSource._create(parameters) end

---@field slot string
function AudioSource:__play(slot) self:_play(slot) end

---@field slot string
function AudioSource:__stop() self:_stop() end