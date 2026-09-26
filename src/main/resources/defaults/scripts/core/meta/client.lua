---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

--- Игровая сессия клиента
---@class GameSession
---@field audio AudioLibrary
---@field main_player Player
game_session = {}