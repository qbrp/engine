---@param module string
function reload(module)
    for name, _ in pairs(package.loaded) do
        if name:match("^" .. module .. "%.") then
            package.loaded[name] = nil
            -- info("Reloaded module " .. name)
        end
    end
end

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