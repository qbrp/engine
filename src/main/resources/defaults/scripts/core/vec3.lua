---@meta

---@class vec3
---@field x number
---@field y number
---@field z number
local vec3 = {}

---@param x? number
---@param y? number
---@param z? number
---@return vec3
function vec3.new(x, y, z)
    return __engine_vec3.new(x, y, z)
end

---@return vec3
function vec3:copy() end

---@param other vec3
function vec3:set(other) end

---@param other vec3
function vec3:add(other) end

---@param other vec3
function vec3:sub(other) end

---@param scalar number
function vec3:mul(scalar) end

---@param scalar number
function vec3:div(scalar) end

function vec3:normalize() end

---@param other vec3
---@return number
function vec3:dot(other) end

---@return number
function vec3:length_squared() end

---@return number
function vec3:length() end

---@return vec3
function vec3:normalized() end

return vec3
