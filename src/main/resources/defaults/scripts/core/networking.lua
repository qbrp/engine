require("core.component")

------------------

---@class EntityRpcQueueComponent : Component
---@field send fun(self: EntityRpcQueueComponent, value: table)
EntityRpcQueueComponent = Component.of("core/networking/entity_rpc_queue")

---@return EntityRpcQueueComponent
function EntityRpcQueueComponent.empty()
    return EntityRpcQueueComponent:construct({})
end

------------------

---@class EntityRpcReceiverComponent : Component
---@field messages EntityRpcMessage[]
EntityRpcReceiverComponent = Component.of("core/networking/entity_rpc_receiver")

---@class EntityRpcMessage
---@field data table
---@field sender Player

---@return EntityRpcReceiverComponent
function EntityRpcReceiverComponent.empty()
    return EntityRpcReceiverComponent:construct({})
end

------------------

---@class DynamicVoxelInterestComponent : Component
DynamicVoxelInterestComponent = Component.of("core/networking/voxel_interest")
DynamicVoxelInterestComponent.instance = DynamicVoxelInterestComponent

---@return DynamicVoxelInterestComponent
function DynamicVoxelInterestComponent.create()
    return DynamicVoxelInterestComponent:construct(DynamicVoxelInterestComponent.instance)
end

------------------
