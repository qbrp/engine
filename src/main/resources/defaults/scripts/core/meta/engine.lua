---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class Engine
---@field SCRIPTS_PATH string абсолютный путь к папке скриптов (engine/scripts)
---@field MODULES_PATH string абсолютный путь к папке модулей (engine/modules)
---@field game_session GameSession?
---@field worlds table<WorldId, World>
---@field player Player метатаблица игрока
---@field logger Logger
---@field modules Modules
---@field id IdLibrary
---@field vec3 Vec3Libary
---@field component ComponentLibrary
---@field reports_collector ReportCollector
---@field reload_script fun(filename: string)?
engine = {}