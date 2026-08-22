require("core.util")
require("core.bridge")

---@class NamespaceOptions
---@field id string
---@field items? ItemConfig[]
---@field scripts? Script[]
---@field components? ComponentTypeSettings[]
---@field intents? Intent[]
---@field systems? System[]

---@class Namespace : NamespaceOptions
Namespace = {} or Namespace
Namespace.__index = Namespace

---@return Namespace
---@param id string
function Namespace.of(id)
    assert(id ~= nil, "id must be not null")
    return setmetatable({ id = id }, Namespace)
end

--------------------------------------------------------------------------------
--- Компиляция
--------------------------------------------------------------------------------

---@class CompilationResult
---@field namespaces? Namespace[]
---@field callbacks? Callbacks
---@field phases? SystemPhase[]
CompilationResult = {}
CompilationResult.__index = CompilationResult

function CompilationResult.new(namespaces)
    local obj = setmetatable({}, CompilationResult)
    obj.namespaces = namespaces or {}   -- поле для конкретного объекта
    return obj
end

---@param namespace NamespaceOptions
function CompilationResult:namespace(namespace)
    assert(namespace.id ~= nil, "namespace.id must be not null")
    table.insert(self.namespaces, namespace)
end

--------------------------------------------------------------------------------
--- Предметы
--------------------------------------------------------------------------------

---@param id string
---@param display_name string
---@param parameters? ItemConfig without id and display_name values
function Namespace:item(id, display_name, parameters)
    assert(id ~= nil, "item id must be not null")
    if (parameters == nil) then parameters = { } end
    parameters.id = id
    parameters.display_name = display_name
    local default_asset = parameters.asset or self.id .. "/" .. id
    if (parameters.assets == nil) then
        parameters.assets = {}
    end
    parameters.assets.default = default_asset
    return parameters
end

--------------------------------------------------------------------------------
--- Контексты скриптов
--------------------------------------------------------------------------------


---@class LoadItemScriptContext
---@field item Entity
---@field world World
LoadItemScriptContext = LoadItemScriptContext

---@class InteractionScriptContext
---@field player Player
---@field raycast_player Player?
InteractionScriptContext = InteractionScriptContext

---@class VoxelActionScriptContext
---@field player Player?
---@field world World
---@field voxel_pos number[]
---@field voxel_meta VoxelMeta
VoxelActionScriptContext = VoxelActionScriptContext

---@class WorkspaceOpenScriptContext
---@field add_window fun(self: WorkspaceOpenScriptContext, id: string, url: string, width: number, height: number): WebWidgetBehaviour
---@field game_session GameSession
---clientside
WorkspaceOpenContext = WorkspaceOpenContext

---@param id string
---@param fun fun(context)
function Script.new(id, fun)
    return setmetatable({ id = id, fun = fun }, Script)
end

--------------------------------------------------------------------------------
--- Системы
--------------------------------------------------------------------------------

---@class SystemPhase
---@field id string
---@field systems string[] systems ids

---@param id string
---@param systems string[]
---@return SystemPhase
function SystemPhase(id, systems)
    assert(#systems > 0, "systems list is empty")
    return {
        id = id,
        systems = systems
    }
end

---@param id string
---@param systems System[]
function CompilationResult:phase(id, systems)
    if self.phases == nil then
        self.phases = empty_table()
    end

    table.insert(
        self.phases,
        SystemPhase(
            id,
            map(systems, function(elem)
                return elem.id
            end)
        )
    )
end

---@class System
---@field id string
---@field query Component[]
---@field side? SystemSide default SERVER
---@field update fun(world: World, entity: Entity, ...)

---@enum SystemSide
SystemSide = {
    SERVER = "server",
    CLIENT = "client",
    BOTH = "both"
}

---@param id string
---@param query Component[]
---@param func? fun(world: World, entity: Entity, ...: Component)
---@param side? SystemSide default SERVER
---@return System
function System(id, query, side, func)
    return {
        id = id,
        query = query,
        update = func or function()
            Log.info(id .. " system update function not defined")
        end,
        side = side or SystemSide.SERVER
    }
end

--------------------------------------------------------------------------------
--- Компоненты
--------------------------------------------------------------------------------

---@class ComponentTypeSettings
---@field id? string
---@field savable? boolean
---@field networking? boolean

---@param components (ComponentTypeSettings|string)[]
---@return ComponentTypeSettings[]
function ComponentList(components)
    return map(components, function(elem)
        local elem_type = type(elem)
        if (elem_type == "string") then
            return { id = elem }
        elseif (elem_type == "table") then
            return elem
        end
    end)
end

---@param name string
---@return ComponentTypeSettings
function NetworkingComponent(name)
    return { id = name, networking = true }
end

---@param name string
---@return ComponentTypeSettings
function SavableComponent(name)
    return { id = name, savable = true }
end

---@param name string
---@return ComponentTypeSettings
function PersistentComponent(name)
    return { id = name, networking = true, savable = true }
end

---
ComponentList { "component_1", "component_2", "component_3" }
---

--------------------------------------------------------------------------------
--- Регистрация
--------------------------------------------------------------------------------

---@class Registration
---@field on_compilation fun(func: fun(): CompilationResult)
Registration = Registration

--------------------------------------------------------------------------------
--- События
--------------------------------------------------------------------------------

---@class Callbacks
---@field player_instantiate? fun(context: Player)
---@field player_destroy? fun(context: Player)
---@field world_tick_20? fun(context: World)
---@field world_tick? fun(context: World)
---@field place_voxel? fun(context: VoxelActionScriptContext)
---@field load_item? fun(context: LoadItemScriptContext)
---@field workspace_open? fun(context: WorkspaceOpenScriptContext)
---@field player_input_tick? fun(context: PlayerInputTickScriptContext)

---@return Callbacks
function CompilationResult:get_or_create_callbacks()
    if self.callbacks ~= nil then
        return self.callbacks
    else
        local callbacks = empty_table()
        self.callbacks = callbacks
        return callbacks
    end
end

---@param fun fun(context: LoadItemScriptContext)
function CompilationResult:on_load_item(fun)
    self:get_or_create_callbacks().load_item = fun
end

---@param fun fun(context: VoxelActionScriptContext)
function CompilationResult:on_place_voxel(fun)
    self:get_or_create_callbacks().place_voxel = fun
end

---@param fun fun(context: World)
function CompilationResult:on_world_tick_20(fun)
    self:get_or_create_callbacks().world_tick_20 = fun
end

---@param fun fun(context: World)
function CompilationResult:on_world_tick(fun)
    self:get_or_create_callbacks().world_tick = fun
end

---@param fun fun(context: WorkspaceOpenScriptContext)
function CompilationResult:on_workspace_open(fun)
    self:get_or_create_callbacks().workspace_open = fun
end

---@param fun fun(context: PlayerInputTickScriptContext)
function CompilationResult:on_player_input_tick(fun)
    self:get_or_create_callbacks().player_input_tick = fun
end

--------------------------------------------------------------------------------
--- Интенты
--------------------------------------------------------------------------------

---@class IntentActor
---@field type string "command", "toolgun" available
---@field player Player?
---@field entity number id

---@class IntentTarget
---@field player Player?
---@field voxel_pos number[]
---@field pos number[]

---@class IntentSelection
---@field pos1 number[] voxel pos
---@field pos2 number[] voxel pos

---@class IntentScriptContext
---@field world World
---@field actor IntentActor
---@field target IntentTarget
---@field inputs table<string, any>
---@field gen_target fun(): IntentTarget
---@field gen_selection fun(): IntentSelection
---@field feedback fun(text: string)
IntentScriptContext = {}

---@param name string
---@param script string id
---@param permission boolean,
---@param inputs? IntentInput[]
---@param actors? string[]
---@return Intent
function Intent.of(id, script, permission, name, inputs, actors)
    return setmetatable({
        id = id,
        name = name,
        permission = permission,
        script = script,
        inputs = inputs,
        actors = actors or { "command", "toolgun" }
    }, Intent)
end
