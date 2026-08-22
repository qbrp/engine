--------------------------------------------------------------------------------
---- Реестры
--------------------------------------------------------------------------------

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
