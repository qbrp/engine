require("core.component")

---@class ServerboundChannelComponent : Component
---@field messages ServerboundChannelMessage[]
ServerboundChannelComponent = Component.of("core/networking/serverbound_channel")

---@class ServerboundChannelMessage
---@field data any
---@field sender Player

---@return ServerboundChannelComponent
function ServerboundChannelComponent.empty()
    return ServerboundChannelComponent:construct({ values = {} })
end

---@class DynamicVoxelInterestComponent : Component
DynamicVoxelInterestComponent = Component.of("core/networking/voxel_interest")
DynamicVoxelInterestComponent.instance = DynamicVoxelInterestComponent

---@return DynamicVoxelInterestComponent
function DynamicVoxelInterestComponent.create()
    return DynamicVoxelInterestComponent:construct(DynamicVoxelInterestComponent.instance)
end