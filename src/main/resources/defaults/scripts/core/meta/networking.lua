---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

--- Очередь исходящих RPC-сообщений сущности.
--- Метод `send` отправляет значение на сервер в `EntityRpcReceiverComponent` той же сущности.
--- Вызовет ошибку на сервере, если сущность или её компонент не будут найдены.
--- **Обрабатывается только на клиенте**
---@class EntityRpcMessage
---@field sender Player
---@field data table

--- Очередь принимаемых RPC-сообщений сущности.
--- Принимает RPC-сообщения, помещённые в клиентскй `EntityRpcQueueComponent` той же сущности.
--- * Список `messages` автоматически очищается каждый тик после выполнения скриптовых систем.
--- * **Обрабатывается только на сервере**
---@class EntityRpcReceiverComponent : Component
---@field messages EntityRpcMessage[]

--- Очередь исходящих RPC-сообщений сущности.
--- Метод `send` отправляет значение на сервер в `EntityRpcReceiverComponent` той же сущности.
--- Вызовет ошибку на сервере, если сущность или её компонент не будут найдены.
--- **Обрабатывается только на клиенте**
---@class EntityRpcQueueComponent : Component
---@field send fun(self: EntityRpcQueueComponent, value: table)

--- Метка динамических вокселей для системы синхронизации сущностей.
--- Если стоит на `Networked`-сущности, система отправляет её снимок на клиент через канал
--- для динамических вокселей.
---@class DynamicVoxelInterestComponent : Component

---@alias JumpComponent boolean
---@alias LocationComponent Vec3