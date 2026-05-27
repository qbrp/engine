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
---- Компоненты
--------------------------------------------------------------------------------

--- Userdata
---@class ComponentType
---@field id string

---@param id string
---@return ComponentType
function component_type_of(id) return _component_type_of(id) end

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
---@field type string "text", "int", "double", "logic", "table" available
---@return IntentInput
function IntentInput.of(id, type)
    return setmetatable({ id = id, type = type }, IntentInput)
end

---@class Intent
---@field id string
---@field name string
---@field script string id
---@field inputs IntentInput[]
---@field actors string[] "command", "toolgun" available, default all,
---@field permission string
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

---@class Sound
---@field id string
---@field stream boolean false default

--- Userdata
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
AudioSource = AudioSource or {}

---@param parameters AudioSource
---@return AudioSource
function AudioSource.__create(parameters) return AudioSource._create(parameters) end

---@field slot string
function AudioSource:__play(slot) self:_play(slot) end

---@field slot string
function AudioSource:__stop() self:_stop() end