local audio = engine.game_session.audio

return {
    default_source = function()
        local source = audio.create({ sound = "test:bell" })
        assert(source.x == 0 and source.y == 0 and source.z == 0)
        assert(source.volume == 1 and source.pitch == 1)
        assert(not source.spatial and not source.looping)
        assert(source.radius == 16)
        assert(source.slot == nil and not source.ended)
        return source
    end,

    configured_source = function()
        local source = audio.create({
            sound = { id = "test:wind", stream = true },
            category = "players",
            x = -2.5,
            y = 4,
            z = 8,
            volume = 0.25,
            pitch = 1.5,
            spatial = true,
            radius = 24,
            looping = true,
        })
        assert(source.x == -2.5 and source.y == 4 and source.z == 8)
        assert(source.volume == 0.25 and source.pitch == 1.5)
        assert(source.spatial and source.looping)
        assert(source.radius == 24)
        return source
    end,

    categories = function()
        local result = {}
        for _, category in ipairs({
            "master", "weather", "blocks", "hostile",
            "neutral", "players", "ambient", "voice",
        }) do
            result[#result + 1] = audio.create({ sound = "test:bell", category = category })
        end
        return result
    end,

    play_and_stop = function()
        local first = audio.create({ sound = "test:first" })
        local second = audio.create({ sound = "test:second" })

        assert(first:play() == nil)
        assert(first.slot == "0" and not first.ended)
        assert(second:play() == nil)
        assert(second.slot == "1" and not second.ended)
        assert(first:stop() == nil)
        assert(first.slot == nil and first.ended)
        assert(second.slot == "1" and not second.ended)
        return first, second
    end,

    invalid_parameters = function()
        for _, parameters in ipairs({ {}, { sound = 3 }, { sound = false } }) do
            local ok, failure = pcall(audio.create, parameters)
            assert(not ok and tostring(failure):find("invalid sound type", 1, true))
        end
        local ok = pcall(audio.create, { sound = "test:bell", category = "unknown" })
        assert(not ok)
    end,
}
