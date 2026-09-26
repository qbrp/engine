---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@alias WorldId string

---@class World
---@field id WorldId идентификатор типа namespace:id (например minecraft:overworld)
---@field is_client boolean
---@field players Player[] (!) при получении значение аллоцируется новый список
world = {}

---@return Entity
function world:add_entity() end

---@alias DynamicVoxel Entity
---@alias DynamicVoxelError
---| '"chunk_not_loaded"'
---| '"position_occupied"'

--- Создать сущность динамического вокселя на позиции `pos`
---@param pos Vec3
---@param networked boolean? true
---@return DynamicVoxel? voxel
---@return DynamicVoxelError? error
function world:add_dynamic_voxel(pos, networked) end

---@param position Vec3
---@return DynamicVoxel?
function world:get_dynamic_voxel(position) end

---@param event Component
---@param type ComponentTypeHolder
---@param networked boolean false
---@return Entity
function world:emit(type, event, networked) end

--- Пройтись по всем сущностям в мире, имеющих компоненты из `types`
--- Функция `fun` принимает в качестве аргументов последовательность полученных компонентов
--- из сущности в порядке `types`
---@param types ComponentTypeHolder[]
---@param fun fun(world: World, entity: Entity, ...)
function world:iterate(types, fun) end