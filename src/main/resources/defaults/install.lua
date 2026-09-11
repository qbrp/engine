--- Автоматически сгенерированный скрипт сброки билда сервера

package.path = __engine.SCRIPTS_PATH .. "/?.lua;"
    .. __engine.SCRIPTS_PATH .. "/?/module.lua;"
    .. __engine.MODULES_PATH .. "/?.lua;"
    .. __engine.MODULES_PATH .. "/?/module.lua;"

local modules = require("core.compilation.modules")
local enabled = modules.enabled
local result = modules.compose(enabled)

return {
    namespaces = result.namespaces,
    callbacks = result.callbacks,
    phases = {

    }
}