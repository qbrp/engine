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

---@param id string
---@param type string "text", "int", "double", "logic", "table" available
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
