require("core.bridge")

----------------------------------------
--- API регистрации
----------------------------------------

---@param namespace Namespace
function CompilationResult:namespace(namespace)
    assert(namespace.id ~= nil, "namespace.id required")
    table.insert(self.namespaces, namespace)
end

------------------

---@class InteractionScriptContext
---@field player Player
---@field raycastPlayer Player?
InteractionScriptContext = {}
InteractionScriptContext.__index = InteractionScriptContext

---@class VoxelActionScriptContext
---@field player Player?
---@field world World
---@field voxel_pos number[]
---@field voxel_meta VoxelMeta
VoxelActionScriptContext = {}
VoxelActionScriptContext.__index = VoxelActionScriptContext

---@param id string
---@param fun fun(context)
function Script.new(id, fun)
    return setmetatable({ id = id, fun = fun }, Script)
end

------------------

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