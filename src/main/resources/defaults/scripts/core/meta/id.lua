---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class IdLibrary
id_library = {}

--- В перечень разрешенных символов входят a-z0-9/._-
---@param namespace string 
---@param loc string
---@return Id
function id_library.new(namespace, loc) end

---@param str IdLiteral
---@return Id id
---@return string? error
function id_library.parse(str) end

---@param str IdReference
---@return string
function id_library.fetch_local(str) end

---@param loc string
---@return boolean is_valid
function id_library.validate_local(loc) end

---@class Id
---@field namespace string
---@field loc string
---@field full string
id = {}

---@alias IdLiteral string {пространство имён}/{локальная часть}, например core/item
---@alias IdReference IdLiteral|Id