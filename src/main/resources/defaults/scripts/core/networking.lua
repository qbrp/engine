require("core.component")

------------------

--- Очередь исходящих RPC-сообщений сущности.
--- Метод `send` отправляет значение на сервер в `EntityRpcReceiverComponent` той же сущности.
--- Вызовет ошибку на сервере, если сущность или её компонент не будут найдены.
--- **Обрабатывается только на клиенте**
---@class EntityRpcQueueComponent : Component
---@field send fun(self: EntityRpcQueueComponent, value: table)
---@see EntityRpcReceiverComponent
EntityRpcQueueComponent = Component.of("core/networking/entity_rpc_queue")

---@return EntityRpcQueueComponent
function EntityRpcQueueComponent.empty()
    return EntityRpcQueueComponent:construct({})
end

------------------

--- Очередь принимаемых RPC-сообщений сущности.
--- Принимает RPC-сообщения, помещённые в клиентскй `EntityRpcQueueComponent` той же сущности.
--- * Список `messages` автоматически очищается каждый тик после выполнения скриптовых систем.
--- * **Обрабатывается только на сервере**
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

--- Метка динамических вокселей для системы синхронизации сущностей.
--- Если стоит на `Networked`-сущности, система отправляет её снимок на клиент через канал
--- для динамических вокселей.
---@class DynamicVoxelInterestComponent : Component
DynamicVoxelInterestComponent = Component.of("core/networking/voxel_interest")
DynamicVoxelInterestComponent.instance = DynamicVoxelInterestComponent

---@return DynamicVoxelInterestComponent
function DynamicVoxelInterestComponent.create()
    return DynamicVoxelInterestComponent:construct(DynamicVoxelInterestComponent.instance)
end

------------------
