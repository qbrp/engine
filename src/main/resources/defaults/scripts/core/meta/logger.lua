---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class Logger
local logger = {}

---@param str string
---@param ... string
function logger.info(str, ...) end

---@param str string
---@param ... string
function logger.warn(str, ...) end

---@param str string
---@param ... string
function logger.error(str, ...) end

---@param str string
---@param ... string
function logger.debug(str, ...) end