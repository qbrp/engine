-- Предмет

---@class ItemConfig
---@field id? string
---@field display_name? string
---@field assets? table<string, string>
---@field asset? string
---@field stack_size? number 1-64
---@field mass? number kg
---@field tooltip? string minimessage
---@field writable? WritableComponentConfig
---@field flashlight? FlashlightComponentConfig
---@field progression_animations? table<string, string>
---@field sound_events? table<string, string>

---@class FlashlightComponentConfig
---@field radius number meters
---@field distance number meters
---@field light number 0-15

---@class WritableComponentConfig
---@field pages number
---@field texture string id
