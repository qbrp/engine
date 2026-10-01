---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class Instant
---@field epoch_seconds integer
---@field nanoseconds integer
local instant = {}

---@class Zone

---@param zone Zone
---@return ZonedDateTime
function instant:zoned(zone) end

---@class ZonedDateTime
---@field zone string

---@class TimeLibrary
---@field instant Instant мета-таблица
---@field zoned_date_time ZonedDateTime мета-таблица
---@field system_zone Zone
local time = {}

---@return Instant
function time.now() end

---@param id string
---@return Zone
function time.zone(id) end

---@param date_time ZonedDateTime
---@param pattern string
---@param locale string?
---@return string
function time.format(date_time, pattern, locale) end
