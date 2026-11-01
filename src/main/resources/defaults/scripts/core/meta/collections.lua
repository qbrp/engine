--- Хэш-мапа на JVM
---@class MutableMap<K, V>
---@field size integer
---@field is_empty boolean
---@field __index fun(self: MutableMap<K, V>, key: K): V?
---@field __len fun(self: MutableMap<K, V>): integer
---@field contains_key fun(self: MutableMap<K, V>, key: K): boolean
---@field remove fun(self: MutableMap<K, V>, key: K): V
---@field clear fun(self: MutableMap<K, V>)
---@field to_table fun(self: MutableMap<K, V>): table<K, V>
local mutable_map = {}

---@class MutableList<T>
---@field size integer
---@field is_empty boolean
---@field [integer] T?
---@field __index fun(self: MutableList<T>, index: integer): T?
---@field __newindex fun(self: MutableList<T>, index: integer, value: T?)
---@field __len fun(self: MutableList<T>): integer
---@field get fun(self: MutableList<T>, index: integer): T?
---@field set fun(self: MutableList<T>, index: integer, value: T?)
---@field add fun(self: MutableList<T>, value: T)
---@field remove_at fun(self: MutableList<T>, index: integer): T
---@field clear fun(self: MutableList<T>)
---@field to_table fun(self: MutableList<T>): T[]
local mutable_list = {}
