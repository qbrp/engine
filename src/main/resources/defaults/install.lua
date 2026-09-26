--- Автоматически сгенерированный скрипт сброки билда сервера

local compilation = ...

package.path = engine.SCRIPTS_PATH .. "/?.lua;"
    .. engine.SCRIPTS_PATH .. "/?/module.lua;"
    .. engine.MODULES_PATH .. "/?.lua;"
    .. engine.MODULES_PATH .. "/?/module.lua;"

require("core.util.table")
require("core.util.string")
require("core.util.math")
require("core.util.reports")

engine.reload_script = function (module)
    package.loaded[module] = nil
    require(module)
end

return require("core.composer.contents").build(compilation)
