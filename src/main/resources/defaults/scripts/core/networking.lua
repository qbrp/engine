require("core.component")

------------------

---@class EntityRpcQueueComponent : Component
---@field values any[]
EntityRpcQueueComponent = Component.of("core/networking/entity_rpc_queue")

---@return EntityRpcQueueComponent
function EntityRpcQueueComponent.empty()
    return EntityRpcQueueComponent:construct({ values = {} })
end

------------------

---@class EntityRpcReceiverComponent : Component
---@field messages EntityRpcMessage[]
EntityRpcReceiverComponent = Component.of("core/networking/entity_rpc_receiver")

---@class EntityRpcMessage
---@field value any
---@field sender Player

---@return EntityRpcReceiverComponent
function EntityRpcReceiverComponent.empty()
    return EntityRpcReceiverComponent:construct({ values = {} })
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