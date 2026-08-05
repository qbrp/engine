LIBRARY_PATH = LIBRARY_PATH
SCRIPTS_PATH = SCRIPTS_PATH
package.path = LIBRARY_PATH .. "/?.lua;" .. SCRIPTS_PATH .. "/?.lua;"

require("core.bridge")
require("core.registration")
require("core.player")
require("core.tween")
require("core.audio")
require("core.light")
require("core.area")
vec3 = require("core.vec3")

Registration.on_compilation(function()
    local result = CompilationResult.new()
    result:setup_player()
    result:setup_tween()
    result:setup_audio()
    result:setup_light()
    result:setup_area()
    return result
end)

Log.info("Loaded standard library")
