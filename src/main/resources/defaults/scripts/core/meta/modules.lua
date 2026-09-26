---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@alias ModuleId string

---@class Modules
---@field enabled Module[]
---@field module Module метатаблица модулей

---@class Module
---@field document ModuleFile
---@field dir ModuleFolder
---@field namespace ModuleId
---@field namespaces string[]
---@field allowed_namespaces table<string, boolean> --- повторяет namespaces, но в виде таблицы для быстрого поиска
---@field depends string[]
local module = {}

--- валидирует идентификатор, если это Id
---@param str IdReference
---@return Id? id
---@return string? error
function module:resolve_id(str) end

---@class ModuleEntryBase
---@field path string
---@field name string

---@class ModuleFile : ModuleEntryBase
---@field kind "file"
local module_file = {}

---@return string
function module_file:read() end

---@class ModuleFolder : ModuleEntryBase
---@field kind "folder"
local module_folder = {}

---@return table<string, ModuleEntry>
function module_folder:files() end

---@alias ModuleEntry ModuleFile | ModuleFolder
