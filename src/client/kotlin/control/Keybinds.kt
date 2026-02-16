package org.lain.engine.client.control

import org.lain.engine.client.mc.KeyBindModifier
import org.lain.engine.client.mc.KeybindId
import org.lain.engine.client.mc.KeybindSettings
import org.lain.engine.client.mc.MinecraftClient
import org.lwjgl.glfw.GLFW

val ADJUST_CHAT_VOLUME = KeybindSettings(
    name = "Увеличить громкость сообщения",
    id = KeybindId("adjust-inputVolume"),
    modifiers = listOf(KeyBindModifier.Control),
    key = GLFW.GLFW_KEY_2,
    onHold = { client ->
        client.gameSession?.vocalRegulator?.increase(0.05f)
    },
)

val DECREASE_CHAT_VOLUME = KeybindSettings(
    name = "Уменьшить громкость сообщения",
    id = KeybindId("decrease-inputVolume"),
    modifiers = listOf(KeyBindModifier.Control),
    key = GLFW.GLFW_KEY_3,
    onHold = { client ->
        client.gameSession?.vocalRegulator?.decrease(0.05f)
    }
)

val RESET_CHAT_VOLUME = KeybindSettings(
    name = "Сбросить громкость сообщения",
    id = KeybindId("reset-inputVolume"),
    modifiers = listOf(KeyBindModifier.Control),
    key = GLFW.GLFW_KEY_0,
    onPress = { client ->
        client.gameSession?.vocalRegulator?.reset()
    }
)

val DEVELOPER_MODE = KeybindSettings(
    name = "Режим разработчика",
    id = KeybindId("dev"),
    GLFW.GLFW_KEY_F10,
    onPress = { client ->
        client.toggleDeveloperMode()
    }
)

val HIDE_INTERFACE = KeybindSettings(
    name = "Скрыть интерфейс",
    id = KeybindId("hide_hud"),
    GLFW.GLFW_KEY_F1,
    onPress = { client ->
        val options = MinecraftClient.options
        options.hudHidden = !options.hudHidden
        client.toggleHudHiding()
    },
)

val ALLOW_SPEED_INTENTION_CHANGE = KeybindSettings(
    name = "Смена скорости ходьбы",
    id = KeybindId("speed"),
    GLFW.GLFW_MOD_ALT,
    onHold = { client -> client.gameSession?.movementManager?.locked = false },
    onRelease = { client -> client.gameSession?.movementManager?.locked = true },
    isMouse = true
)

val TOGGLE_CHAT_SPY = KeybindSettings(
    name = "Переключить слежку",
    id = KeybindId("toggle-spy"),
    key = GLFW.GLFW_KEY_N,
    onPress = { client ->
        client.gameSession?.chatManager?.toggleSpy()
    }
)

val EXTEND_HAND = KeybindSettings(
    name = "Выставить руку",
    id = KeybindId("extend-arm"),
    key = GLFW.GLFW_KEY_Y,
    onPress = { client ->
        client.gameSession?.apply { extendArm = !extendArm }
    }
)