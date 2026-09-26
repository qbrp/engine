---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@alias ReportSeverity
---| "warning"
---| "error"
---| "fatal"

---@alias CompilationPhase
---| "install" # загрузка скриптов и выполнение функций
---| "compilation" # маппинг
---| "validation"
---| "linking" # связь сущностей через идентификаторы

---@alias BuiltinContentKind
---| "item"
---| "sound"
---| "script"
---| "component"
---| "operation"
---| "system"
---| "phase"
---| "other"

---@alias ContentKind BuiltinContentKind|string

---@class ReportLocation
---@field source string
---@field line integer?
---@field column integer?
---@field path string?

---@class ReportTarget
---@field kind ContentKind
---@field local_id string

---@class Report
---@field severity ReportSeverity
---@field message string
---@field phase CompilationPhase
---@field namespace string?
---@field location ReportLocation?
---@field target ReportTarget?

---@class ReportCollector
local reports = {}

---@param rep Report
function reports:report(rep) end

---@param rep Report
function reports:abort(rep) end

---@param namespace string
---@param id Id
---@param kind ContentKind
function reports:report_invalid_namespace(namespace, id, kind) end

---@class CompilationContext
---@field reports ReportCollector

---@class EventListeners
---@field player_instantiate fun(player: Player)?
---@field player_destroy fun(player: Player)?
---@field world_tick_20 fun(world: World)?
---@field world_tick fun(world: World)?
---@field place_voxel fun(context: VoxelActionContext)?
---@field item_load fun(context: LoadItemContext)?
---@field show_item_tooltip fun(context: ItemTooltipContext)?
---@field player_input_tick fun(context: PlayerInputTickContext)?

---@class InventoryTab
---@field entries InventoryTabEntry[]

---@class InventoryTabEntry
---@field prefab IdReference
---@field tooltip string[]

---@class Build
---@field namespaces NamespaceDraft[]
---@field listeners EventListeners?
---@field inventory_tab InventoryTab?
---@field root_phase SystemPhase

---@class ContentsDraft
---@field items ItemPrefabDraft[]
---@field scripts Script[]
---@field operations OperationDraft[]
---@field components ComponentTypeSettings[]
---@field systems SystemDraft[]
---@field sound_events SoundEventDraft[]
---@field progression_animations ProgressionAnimationDraft[]

---@class NamespaceDraft : ContentsDraft
---@field id string

---@class SoundEventDraft
---@field id IdReference
---@field sources SoundSourceDraft[]

---@class SoundSourceDraft
---@field id IdReference
---@field volume number
---@field pitch number
---@field weight integer
---@field distance integer
---@field pitch_random number

---@class ProgressionAnimationDraft
---@field id IdReference
---@field frames string[]|ProgressionAnimationFramesDraft
---@field text string
---@field success string?

---@class ProgressionAnimationFramesDraft
---@field name string
---@field count integer

---@class SystemPhase
---@field name string
---@field steps PhaseStep[]

---@class PhaseStep
---@field type string

---@class PhaseStep.Phase : PhaseStep
---@field type "phase"
---@field phase SystemPhase

---@class PhaseStep.System : PhaseStep
---@field type "system"
---@field system IdReference

---@class SystemDraft
---@field id IdReference
---@field query ComponentTypeReference[]
---@field side SystemSide
---@field tick fun(world: World, entity: Entity, ...: Component)

---@alias SystemSide
---| "server"
---| "client"
---| "both"

---@class ComponentTypeSettings
---@field id IdReference
---@field replicating boolean? false
---@field persistent boolean? false

---@class Script
---@field id IdReference
---@field execute fun(context: ScriptContext)

---@class ItemPrefabDraft
---@field id IdReference
---@field max_count integer
---@field assets table<string, IdReference>
---@field on_load fun(world: World, item: WriteOnlyEntity)

---@class OperationDraft
---@field id IdReference
---@field execute fun(context: OperationContext)
---@field name string? id
---@field inputs OperationInputDraft[]? empty
---@field permission boolean? false

---@class OperationInputDraft
---@field id IdReference
---@field type OperationInputType

---@alias OperationInputType
---| "text"
---| "int"
---| "double"
---| "logic"
---| "table"
