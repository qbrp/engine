---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class AudioLibrary
local audio_library = {}

---@param params AudioSourceParameters
function audio_library.create_source(params) end

---@class Sound
---@field id SoundId
---@field stream boolean false

---@alias SoundId string
---@alias SoundReference Sound|SoundId

---@alias SoundCategory
---| "master"
---| "weather"
---| "blocks"
---| "hostile"
---| "neutral"
---| "players"
---| "ambient"
---| "voice"

---@class AudioSourceParameters
---@field sound SoundReference
---@field category SoundCategory? ambient
---@field x number? 0
---@field y number? 0
---@field z number? 0
---@field looping boolean? false
---@field spatial boolean? false
---@field volume number? 1
---@field pitch number? 1
---@field radius integer? 16

---@class AudioSource
---@field sound SoundId
---@field category SoundCategory
---@field x number
---@field y number
---@field z number
---@field looping boolean
---@field spatial boolean
---@field volume number 0 to 1
---@field pitch -1 to 2
---@field ended boolean false
---@field slot string
---@field radius integer 16
local audio_source = {}

function audio_source:play() end

function audio_source:stop() end