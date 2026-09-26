---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как это может сломать поведение скриптов.

---@class ScriptContext

---@class LoadItemContext : ScriptContext
---@field item Entity
---@field world World


---@class ItemTooltipContext : ScriptContext
---@field world World
---@field item Entity
---@field lines string[]


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
---@field target table?
---@field inputs table[]
---@field gen_target fun(): table
---@field gen_selection fun(): table?
---@field feedback fun(message: string)


---@class OperationActor
---@field type string
---@field player Player?
---@field entity integer?


---@class WorkspaceOpenContext : ScriptContext
---@field add_window fun(self: WorkspaceOpenContext, id: string, url: string, width: number, height: number): WebWidgetBehaviour
---@field game_session GameSession