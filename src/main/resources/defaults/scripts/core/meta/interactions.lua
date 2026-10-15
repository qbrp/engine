---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@alias InputActionType
---| "attack"
---| "base"
---| "take_off"

---@class InputAction
---@field type InputActionType

---@class Verb
---@field id string
---@field name string
---@field priority integer? 10
---@field input_action InputActionType?
---@field command_type ComponentTypeHolder<Component>? тип компонента, возвращаемого Lua-функцией
---@field create_command fun(): Component

---@class VerbLookupComponent : Component
---@field input InputAction[]
---@field verbs Verb[]

---@alias HandType
---| "main"
---| "offhand"

---@class HandComponent : Component
---@field owner Entity
---@field side HandType
---@field opposite Entity
---@field item Entity?

---@class InteractorComponent : Component
---@field owner Entity

---@class UsingItemComponent : Component
---@field item Entity

---@class InteractionInterruptComponent : Component
