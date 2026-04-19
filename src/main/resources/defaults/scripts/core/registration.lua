require("core.util")
require("core.bridge")

--------------------------------------------------------------------------------
--- Утилиты
--------------------------------------------------------------------------------

---@param namespace Namespace
function CompilationResult:namespace(namespace)
    assert(namespace.id ~= nil, "namespace.id required")
    table.insert(self.namespaces, namespace)
end

--------------------------------------------------------------------------------
--- Контексты скриптов
--------------------------------------------------------------------------------

---@class InteractionScriptContext
---@field player Player
---@field raycast_player Player?
InteractionScriptContext = {}

---@class VoxelActionScriptContext
---@field player Player?
---@field world World
---@field voxel_pos number[]
---@field voxel_meta VoxelMeta
VoxelActionScriptContext = {}

---@param id string
---@param fun fun(context)
function Script.new(id, fun)
    return setmetatable({ id = id, fun = fun }, Script)
end

--------------------------------------------------------------------------------
--- Компоненты
--------------------------------------------------------------------------------

---@param id string
---@return ComponentTypeSettings
function SavableComponent(id)
    return ComponentTypeSettings.of(id)
end

---@param components ComponentTypeSettings[]|string[]
---@return ComponentTypeSettings[]
function ComponentList(components)
    return map(components, function(elem)
        local elem_type = type(elem)
        if (elem_type == "string") then
            return ComponentTypeSettings.of(elem)
        elseif (elem_type == "table") then
            return elem
        end
    end)
end

---
ComponentList { "component_1", "component_2", SavableComponent("component_3") }
---

--------------------------------------------------------------------------------
--- События
--------------------------------------------------------------------------------

---@return Callbacks
function Callbacks.build()
    return setmetatable({}, Callbacks)
end

---@return Callbacks
---@param fun fun(context: VoxelActionScriptContext)
function Callbacks:on_place_voxel(fun)
    self.place_voxel = fun
    return self
end

---@return Callbacks
---@param fun fun(context: World)
function Callbacks:on_world_tick_20(fun)
    self.world_tick_20 = fun
    return self
end

---@return Callbacks
---@param fun fun(context: World)
function Callbacks:on_world_tick(fun)
    self.world_tick = fun
    return self
end

------------------

---@param types ComponentType[]|Component[]
---@param fun fun(world: World, entity: number, ...)
---@param env string client or server
---@return Callbacks
function Callbacks:system(types, fun, env)
    local env = env or "server"
    if (self.systems == nil) then
        self.systems = {}
    end
    table.insert(self.systems, function(world)
        if (env == "client" and not world.is_client) then
            return
        end
        world:iterate_in(types, fun)
    end)
    return self
end

------------------

function Callbacks:submit()
    if (self.systems ~= nil) then
        local base_world_tick = self.world_tick
        self:on_world_tick(function(world)
            if (base_world_tick ~= nil) then base_world_tick(world) end
            for index, value in ipairs(self.systems) do
                value(world)
            end
        end)
    end

    callbacks(self)
end

--------------------------------------------------------------------------------
--- Интенты
--------------------------------------------------------------------------------

---@class IntentActor
---@field type string "command", "toolgun" available
---@field player Player?
---@field entity number id
IntentActor = {}

---@class IntentTarget
---@field player Player?
---@field voxel_pos number[]
---@field pos number[]
IntentTarget = {}

---@class IntentScriptContext
---@field world World
---@field actor IntentActor
---@field target IntentTarget
---@field inputs table<string, any>
---@field gen_target fun(): IntentTarget
IntentScriptContext = {}

---@field name string
---@field script string id
---@field inputs IntentInput[]
---@field actors string[]
---@return Intent
function Intent.of(script, name, inputs, actors)
    return setmetatable({
        id = script.id or script,
        name = name,
        script = script.id or script,
        inputs = inputs,
        actors = actors or { "command", "toolgun" }
    }, Intent)
end
