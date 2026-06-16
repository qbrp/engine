package.path = LIBRARY_PATH .. "/?.lua;" .. SCRIPTS_PATH .. "/?.lua;"
require("core.bridge")
require("core.registration")
require("core.area")

compilation(function()
    local result = CompilationResult.new()
    result:namespace {
        id = "core/player",
        components = ComponentList { "freeze" }
    }
    result:namespace {
        id = "core/sound",
        components = ComponentList { "component", "repeatable", "voxel" }
    }
    result:namespace {
        id = "core/tween",
        components = ComponentList { "container", "target" }
    }
    result:namespace {
        id = "core/light",
        components = ComponentList {
            { id = "flashing", savable = true, networking = true }
        }
    }
    result:namespace {
        id = "core/area",
        components = ComponentList {
            { id = "map_persistent", savable = true },
            { id = "map_state"}
        },
        intents = {
            Intent.of(
                    "create_area",
                    "core/area/create_area",
                    true,
                    "Создать зону",
                    { IntentInput.of("id", "text") }
            ),
            Intent.of(
                    "list_areas",
                    "core/area/list_areas",
                    true,
                    "Вывести список зон"
            ),
            Intent.of(
                "relative_tp",
                "core/area/relative_tp",
                true,
                "Относительная телепортация",
                { IntentInput.of("from", "text"), IntentInput.of("to", "text") }
            )
        },
        scripts = {
            Script.new("create_area", CreateAreaScript),
            Script.new("list_areas", ListAreasScript),
            Script.new("relative_tp", RelativeTeleportationScript)
        }
    }
    return result
end)

Log.info("Loaded standard library")