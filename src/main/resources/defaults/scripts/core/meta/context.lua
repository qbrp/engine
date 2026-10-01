---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как это может сломать поведение скриптов.

---@class ScriptContext

---@class ItemPersistentData
---@field prefab_id Id
---@field count integer
---@field max_count integer

---@class EntityMaterializationContext : ScriptContext
---@field entity Entity
---@field world World
---@field persistent_id string
---@field item ItemPersistentData?

---@class ItemTooltipContext : ScriptContext
---@field game_session GameSession
---@field world World
---@field item Entity


---@class InteractionContext : ScriptContext
---@field player Player
---@field raycast_player Player?


---@class PlayerInputTickContext : ScriptContext
---@field player Player
---@field actions table[]
---@field last_actions table[]
---@field is_spectating boolean
---@field social_interaction_distance number
---@field extend_arm boolean

---@class VoxelMeta
---@field id string
---@field tags string[]
---@field has_tag fun(tag: string): boolean

---@class VoxelActionContext : ScriptContext
---@field player Player?
---@field world World
---@field voxel_pos number[]
---@field voxel_meta VoxelMeta

---@class OperationContext : ScriptContext
---@field world World
---@field actor OperationActor
---@field target OperationTarget?
---@field inputs table<string, any>
---@field gen_target fun(): OperationTarget
---@field gen_selection fun(): OperationSelection?
---@field feedback fun(message: string)

---@class OperationTarget
---@field player Player?
---@field voxel_pos integer[] `{x, y, z}`
---@field pos Vec3

---@class OperationSelection
---@field pos1 integer[] `{x, y, z}`
---@field pos2 integer[] `{x, y, z}`

---@class OperationInputResolutionContext : ScriptContext
---@field world World
---@field actor OperationActor
---@field operation_id string
---@field input_id string
---@field inputs table<string, ScriptValue> Previously resolved input values


---@class OperationActor
---@field type "command"|"toolgun"
---@field player Player
---@field entity integer


---@class WorkspaceOpenContext : ScriptContext
---@field add_window fun(self: WorkspaceOpenContext, id: string, url: string, width: number, height: number): WebWidgetBehaviour
---@field game_session GameSession
