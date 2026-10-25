---@class Contents
---@field equipment_slots table<Id, EquipmentSlot>

---@class ContentsDraft : table<CategoryId, Category>
---@field item_prefabs ItemPrefabDraft[]
---@field scripts Script[]
---@field operations OperationDraft[]
---@field components ComponentTypeSettings[]
---@field systems SystemDraft[]
---@field sound_events SoundEventDraft[]
---@field progression_animations ProgressionAnimationDraft[]
---@field equipment_slots EquipmentSlotDraft[]

---@alias ContentKind BuiltinContentKind|string

---@alias BuiltinContentKind
---| "item"
---| "sound"
---| "script"
---| "component"
---| "operation"
---| "system"
---| "phase"
---| "equipment_slot"
---| "other"