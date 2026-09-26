---@meta

--- Этот файл нужен для поддержки LuaLS-аннотаций.
--- Не импортируйте его, так как он может сломать поведение скриптов.

---@class Vec3Libary
---@field functions Vec3.Functions метатаблица трехмерного вектора, содержащая функции
---@field operator_functions Vec3.OperatorFunctions
local vec3_library = {}

---@param x number? 0
---@param y number? 0
---@param z number? 0
function vec3_library.new(x, y, z) end

---@class Vec3 : Vec3.Functions, Vec3.OperatorFunctions
---@field x number
---@field y number
---@field z number

---@class Vec3.OperatorFunctions
---@operator add(Vec3): Vec3
---@operator sub(Vec3): Vec3
---@operator unm: Vec3
---@operator mul(number): Vec3
---@operator div(number): Vec3
---@operator eq(Vec3): boolean
local operator_functions = {}

---@param left Vec3
---@param right Vec3
---@return Vec3
function operator_functions.__add(left, right) end

---@param left Vec3
---@param right Vec3
---@return Vec3
function operator_functions.__sub(left, right) end

---@param value Vec3
---@return Vec3
function operator_functions.__unm(value) end

---@param left Vec3|number
---@param right Vec3|number
---@return Vec3
function operator_functions.__mul(left, right) end

---@param value Vec3
---@param scalar number
---@return Vec3
function operator_functions.__div(value, scalar) end

---@param left Vec3
---@param right Vec3
---@return boolean
function operator_functions.__eq(left, right) end

---@param value Vec3
---@return string
function operator_functions.__tostring(value) end

---@class Vec3.Functions
local functions = {}

---@return Vec3
function functions:copy() end

---@param other Vec3
function functions:set(other) end

---@param other Vec3
function functions:add(other) end

---@param other Vec3
function functions:sub(other) end

---@param scalar number
function functions:mul(scalar) end

---@param scalar number
function functions:div(scalar) end

function functions:normalize() end

---@param other Vec3
---@return number
function functions:dot(other) end

---@return number
function functions:length_squared() end

---@return number
function functions:length() end

---@return Vec3
function functions:normalized() end