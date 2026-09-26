local array = {}
local methods = {}
local metatable = { __index = methods }
function array.wrap(values)
    return setmetatable(values, metatable)
end
function array.of(...)
    local values = {}

    for index = 1, select("#", ...) do
        values[index] = select(index, ...)
    end

    return array.wrap(values)
end
function array.copy(values)
    local result = {}

    for index = 1, #values do
        result[index] = values[index]
    end

    return result
end
function array.random(values)
    if #values == 0 then
        return nil
    end

    return values[math.random(1, #values)]
end
function array.append(target, source)
    for _, value in ipairs(source) do
        target[#target + 1] = value
    end

    return target
end
function array.index_of(values, value)
    for index, current in ipairs(values) do
        if current == value then
            return index
        end
    end

    return -1
end
function array.contains(values, value)
    return array.index_of(values, value) ~= -1
end
function array.remove_value(values, value)
    local index = array.index_of(values, value)
    if index == -1 then
        return false
    end

    table.remove(values, index)
    return true
end
function array.insert_unique(values, position_or_value, value)
    local position = #values + 1
    local value_to_insert = position_or_value

    if value ~= nil then
        position = position_or_value
        value_to_insert = value
    end

    if array.contains(values, value_to_insert) then
        return false
    end

    table.insert(values, position, value_to_insert)
    return true
end
function array.shuffle_in_place(values)
    for index = #values, 2, -1 do
        local other = math.random(1, index)
        values[index], values[other] = values[other], values[index]
    end

    return values
end
function array.slice(values, start_index, stop_index)
    local start = math.max(start_index or 1, 1)
    local stop = math.min(stop_index or #values, #values)
    local result = {}

    for index = start, stop do
        table.insert(result, values[index])
    end

    return result
end
function array.map(values, func)
    local result = {}

    for index, value in ipairs(values) do
        result[index] = func(index, value)
    end

    return result
end
function array.map_in_place(values, func)
    for index, value in ipairs(values) do
        values[index] = func(index, value)
    end

    return values
end
function array.filter(values, predicate)
    local size = #values
    local result_index = 1

    for index = 1, size do
        local value = values[index]
        if predicate(index, value) then
            values[result_index] = value
            result_index = result_index + 1
        end
    end

    for index = result_index, size do
        values[index] = nil
    end

    return values
end
function array.flatten(values)
    local result = {}

    for _, value in ipairs(values) do
        if type(value) == "table" then
            for _, nested in ipairs(value) do
                table.insert(result, nested)
            end
        else
            table.insert(result, value)
        end
    end

    return result
end
function array.deep_flatten(values)
    local result = {}

    local function append(value)
        if type(value) == "table" then
            for _, nested in ipairs(value) do
                append(nested)
            end
        else
            table.insert(result, value)
        end
    end

    append(values)
    return result
end
function methods.copy(self)
    return array.wrap(array.copy(self))
end
function methods.random(self)
    return array.random(self)
end
function methods.index_of(self, value)
    return array.index_of(self, value)
end
function methods.contains(self, value)
    return array.contains(self, value)
end
function methods.remove_value(self, value)
    return array.remove_value(self, value)
end
function methods.insert_unique(self, position_or_value, value)
    return array.insert_unique(self, position_or_value, value)
end
function methods.shuffle(self)
    return array.shuffle_in_place(self)
end
function methods.slice(self, start_index, stop_index)
    return array.wrap(array.slice(self, start_index, stop_index))
end
function methods.map(self, func)
    return array.wrap(array.map(self, func))
end
function methods.map_in_place(self, func)
    array.map_in_place(self, func)
    return self
end
function methods.filter(self, predicate)
    return array.filter(self, predicate)
end
function methods.flatten(self)
    return array.wrap(array.flatten(self))
end
function methods.deep_flatten(self)
    return array.wrap(array.deep_flatten(self))
end

return array
