---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@alias Biology
---|"human"
---|"humanlike"
---|"humanoid"
---|"creature"

---@alias Sex
---|"male"
---|"female"
---|"other"

---@alias BodyType
---|"slim"
---|"normal"
---|"broad"

---@class CharacterAppearance
---@field breastSize number?
---@field eyeLevel integer 2
---@field description string

---@class GradientName
---@field text string
---@field color1 integer
---@field color2 integer

---@class Skin
---@field url string

---@class Look
---@field id string
---@field title string
---@field skin Skin
---@field description string
---@field base boolean

---@class Character
---@field name GradientName
---@field biology Biology
---@field sex Sex
---@field height number
---@field body_type BodyType
---@field appearance CharacterAppearance
---@field looks Look[]
---@field base_look Look

---@alias CharacterComponent Character
---@alias LookComponent Look
