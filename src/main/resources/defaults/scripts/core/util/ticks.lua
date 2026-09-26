local ticks = {}

---@param seconds number
---@return number
function ticks.seconds(seconds)
    return seconds * 20
end

---@param minutes number
---@return number
function ticks.minutes(minutes)
    return minutes * 60 * 20
end

return ticks
