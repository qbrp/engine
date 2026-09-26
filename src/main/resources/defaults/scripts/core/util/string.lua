---@param value string
---@param prefix string
---@return boolean
function string.starts_with(value, prefix)
    return value:sub(1, #prefix) == prefix
end

---@param value string
---@param suffix string
---@return boolean
function string.ends_with(value, suffix)
    return suffix == "" or value:sub(-#suffix) == suffix
end