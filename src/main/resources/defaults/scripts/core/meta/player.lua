---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class Player : Entity
---@field uuid string уникальный идентификатор, изначальный java uuid
---@field id integer идентификатор сущности, уникален для каждого мира
---@field entity Entity
player = {}

---@param permission string
---@return boolean # всегда возвращает true в одиночном мире
function player:has_permission(permission) end

---@class Narration
---@field message string
---@field time integer
---@field kick boolean

---@param narr Narration
function player:narration(narr) end

---@param command string
---@param root boolean false
function player:invoke_command(command, root) end

---@param type ComponentType
---@param action Component
function player:sync_action(type, action) end

---@alias PlayerMode
---| "default"
---| "spectator"
---| "game_master"

---@class PlayerComponent : Component
---@field player Player

---@class PlayerModeComponent : Component
---@field mode PlayerMode
---@field is_spectator boolean
---@field is_game_master boolean

---@class PlayerPhysicsComponent : Component
---@field no_clip boolean
---@field collides VoxelMeta[] # readonly

---@class PlayerInputComponent : Component
---@field is_sprinting boolean

---@class PlayerAttributesComponent : Component
---@field speed number
---@field fly_speed number
---@field jump_strength number
---@field gravity number

---@class PlayerCustomAttributesComponent : Component
---@field speed number?
---@field jump_strength number?
---@field gravity number?
---@field [string] any

---@class MovementStatusComponent : Component
---@field intention number
---@field stamina number

---@class PlayerVelocityComponent : Component
---@field motion Vec3
---@field previous Vec3

---@class PlayerInventoryComponent : Component
---@field main_hand_item Entity?
---@field off_hand_item Entity?
---@field selected_slot integer
---@field items Entity[]