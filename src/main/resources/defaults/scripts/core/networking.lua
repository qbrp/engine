require("core.component")

---@class ServerboundChannelComponent : Component
---@field values any[]
ServerboundChannelComponent = Component.of("core/networking/serverbound_channel")

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