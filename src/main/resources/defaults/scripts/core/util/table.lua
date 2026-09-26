function table.is_array(t)
    local count = 0
    local max_index = 0

    for key in pairs(t) do
        if type(key) ~= "number"
            or key % 1 ~= 0
            or key < 1
        then
            return false
        end

        count = count + 1
        max_index = math.max(max_index, key)
    end

    return count == max_index
end

---@generic V
---@param t table<any, V>
---@return V[]
function table.values(t)
    local values = {}

    for _, value in pairs(t) do
        values[#values + 1] = value
    end

    return values
end

---@generic K, V
---@param source table<K, V>
---@return table<K, V>
function table.shallow_copy(source)
    local copy = {}

    for key, value in pairs(source) do
        copy[key] = value
    end

    return copy
end

---@generic K, V
---@param t table<K, V>
---@return table<K, V>
function table.deep_copy(t)
    local copies = {}

    local function copy(value)
        if type(value) ~= "table" then
            return value
        end

        if copies[value] then
            return copies[value]
        end

        local result = {}
        copies[value] = result

        for key, item in pairs(value) do
            result[copy(key)] = copy(item)
        end

        return setmetatable(result, getmetatable(value))
    end

    return copy(t)
end

---@param value table
---@param recursive boolean?
---@param indent integer?
---@param seen table<table, boolean>?
---@return string
function table.tostring_deep(value, recursive, indent, seen)
    local resolved_indent = indent or 2
    local indent_string = string.rep(" ", resolved_indent)
    local visited = seen or {}
    local output = {}

    for key, entry in pairs(value) do
        local representation

        if entry == value then
            representation = "self"
        elseif recursive and type(entry) == "table" then
            if visited[entry] then
                representation = tostring(entry) .. " repeat"
            else
                visited[entry] = true
                representation = table.tostring_deep(entry, true, resolved_indent + 2, visited)
            end
        else
            representation = tostring(entry)
        end

        table.insert(output, tostring(key) .. " : " .. representation)
    end

    if #output == 0 then
        return "empty"
    end

    local separator = ",\n" .. indent_string

    return "{\n"
        .. indent_string
        .. table.concat(output, separator)
        .. "\n"
        .. string.rep(" ", resolved_indent - 2)
        .. "}"
end

---@generic T, R
---@param list T[]
---@param statement fun(value: T): R
---@return R[]
function table.map(list, statement)
    local result = {}

    for index = 1, #list do
        result[index] = statement(list[index])
    end

    return result
end

---@generic T
---@param list T[]
---@param statement fun(value: T): boolean
---@return T[]
function table.filter(list, statement)
    local result = {}

    for index = 1, #list do
        local value = list[index]
        if (statement(value)) then
            result[index] = value
        end
    end

    return result
end

---@generic T
---@return T[]
function table.empty()
    return {}
end