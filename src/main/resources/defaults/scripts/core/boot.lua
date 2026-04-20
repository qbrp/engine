package.path = LIBRARY_PATH .. "/?.lua;" .. SCRIPTS_PATH .. "/?.lua;"
require("core.bridge")
require("core.registration")

compilation(function()
    local result = CompilationResult.new()
    result:namespace {
        id = "core/player",
        components = ComponentList { "freeze" }
    }
    result:namespace {
        id = "core/sound",
        components = ComponentList { "component", "repeatable" }
    }
    return result
end)

info("Loaded standard library")