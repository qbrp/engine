---@param value number
---@param minimum number
---@param maximum number
---@return number
function math.clamp(value, minimum, maximum)
    return math.max(minimum, math.min(value, maximum))
end

---@param start number
---@param target number
---@param alpha number
---@return number
function math.lerp(start, target, alpha)
    return start * (1 - alpha) + target * alpha
end

---@param value number
---@return number
function math.smoothstep(value)
    return 3 * value * value - 2 * value * value * value
end

---@param value number
---@return number
function math.smootherstep(value)
    return 6 * value ^ 5 - 15 * value ^ 4 + 10 * value ^ 3
end
