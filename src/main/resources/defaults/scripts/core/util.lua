---@param module string
function reload(module)
    for name, _ in pairs(package.loaded) do
        if name:match("^" .. module .. "%.") then
            package.loaded[name] = nil
            -- info("Reloaded module " .. name)
        end
    end
end

---@class Log
---@field info fun(str: string)
---@field debug fun(module: string, str: string)
Log = Log

---@type fun(): boolean
is_client = is_client

--- Сохранить значение на всю игровую сессию
---@type fun(default: any, slot: string, module?: string)
remember = remember

---@param time number
---@return number
function seconds(time)
    return time * 20
end

---@generic T, R
---@param list T[]
---@param fun fun(elem: T): R
---@return R[]
function map(list, fun)
    local result = {}
    for i = 1, #list do
        result[i] = fun(list[i])
    end
    return result
end

---@generic T
---@param list T[]
---@param fun fun(elem: T)
function for_each(list, fun)
    for i = 1, #list do fun(list[i]) end
end

---@param t table
---@param indent number
---@param recursive boolean
---@param seen table
---@return string
function tablestr(t, recursive, indent, seen)
    indent = indent or 2
    local indentStr = string.rep(" ", indent)
    recursive = recursive or false
    seen = seen or {}
    local output = {}
    for key, value in pairs(t) do
        local value_representation
        if (value == t) then
            value_representation = "self"
        elseif (recursive and type(value) == "table") then
            if (seen[value]) then
                value_representation = tostring(value) .. " repeat"
            else
                seen[value] = true
                value_representation = tablestr(value, true, indent + 2, seen)
            end
        end
        table.insert(output, key .. " : " .. tostring(value))
    end
    local sep = ",\n" .. indentStr
    if (#output == 0) then
        return "empty"
    end
    return "{\n"
            .. indentStr
            .. table.concat(output, sep)
            .. "\n"
            .. string.rep(" ", indent - 2)
            .. "}"
end

---@generic T
---@return T
function empty_table()
    return {}
end