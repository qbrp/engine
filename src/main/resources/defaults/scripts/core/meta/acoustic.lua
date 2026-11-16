---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class AcousticResult
---@field volume_at fun(self: AcousticResult, pos: Vec3): number
---@field free fun(self: AcousticResult)