package org.lain.engine.client.mc

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lain.engine.CommonEngineServerMod
import org.lain.engine.client.EngineClient
import org.lain.engine.util.inject
import org.lwjgl.glfw.GLFW

object KeybindManager {
    private val keybinds = mutableMapOf<KeybindId, EngineKeybind>()

    val ADJUST_CHAT_VOLUME = KeybindSettings(
        name = "Увеличить громкость сообщения",
        id = KeybindId("adjust-inputVolume"),
        modifiers = listOf(KeyBindModifier.Control),
        key = GLFW.GLFW_KEY_2,
        onHold = { client ->
            client.gameSession?.vocalRegulator?.increase(0.05f)
        },
    ).register()

    val DECREASE_CHAT_VOLUME = KeybindSettings(
        name = "Уменьшить громкость сообщения",
        id = KeybindId("decrease-inputVolume"),
        modifiers = listOf(KeyBindModifier.Control),
        key = GLFW.GLFW_KEY_3,
        onHold = { client ->
            client.gameSession?.vocalRegulator?.decrease(0.05f)
        }
    ).register()

    val RESET_CHAT_VOLUME = KeybindSettings(
        name = "Сбросить громкость сообщения",
        id = KeybindId("reset-inputVolume"),
        modifiers = listOf(KeyBindModifier.Control),
        key = GLFW.GLFW_KEY_0,
        onPress = { client ->
            client.gameSession?.vocalRegulator?.reset()
        }
    ).register()

    val DEVELOPER_MODE = KeybindSettings(
        name = "Режим разработчика",
        id = KeybindId("dev"),
        GLFW.GLFW_KEY_F12,
        onPress = { client ->
            client.toggleDeveloperMode()
        }
    ).register()

    val HIDE_INTERFACE = KeybindSettings(
        name = "Скрыть интерфейс",
        id = KeybindId("hide_hud"),
        GLFW.GLFW_KEY_F1,
        onPress = { client ->
            val options = MinecraftClient.options
            options.hudHidden = !options.hudHidden
            client.toggleHudHiding()
        },
        category = KeyBinding.UI_CATEGORY
    ).register()

    val ALLOW_SPEED_INTENTION_CHANGE = KeybindSettings(
        name = "Смена скорости ходьбы",
        id = KeybindId("speed"),
        GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
        onHold = { client -> client.gameSession?.movementManager?.locked = false },
        onRelease = { client -> client.gameSession?.movementManager?.locked = true },
        isMouse = true
    ).register()

    val OPEN_OPTIONS = KeybindSettings(
        name = "Настройки",
        id = KeybindId("open-settings"),
        key = GLFW.GLFW_KEY_O,
        onPress = {
            val configScreen by inject<Screen>()
            MinecraftClient.setScreen(configScreen)
        }
    ).register()

    val TOGGLE_CHAT_SPY = KeybindSettings(
        name = "Переключить слежку",
        id = KeybindId("toggle-spy"),
        key = GLFW.GLFW_KEY_N,
        onPress = { client ->
            client.gameSession?.chatManager?.toggleSpy()
        }
    ).register()

    fun registerKeybinding(keybinding: KeybindSettings): EngineKeybind {
        val type = when(keybinding.isMouse) {
            true -> InputUtil.Type.MOUSE
            false -> InputUtil.Type.KEYSYM
        }
        val fabricKeybinding = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                keybinding.name,
                type,
                keybinding.key,
                keybinding.category
            )
        )
        return EngineKeybind(keybinding, fabricKeybinding)
            .also { keybinds[it.settings.id] = it }
    }


    fun tick(engineClient: EngineClient) {
        for ((id, keybind) in keybinds) {
            val settings = keybind.settings
            val modifiers = settings.modifiers
            val dev = settings.dev
            val requireWorld = settings.requireWorld

            if (dev && !engineClient.developerMode) continue
            if (MinecraftClient.world == null && requireWorld) continue
            if (modifiers.contains(KeyBindModifier.Control) && !Screen.hasControlDown()) continue

            val isPressed = keybind.isPressed
            val wasPressed = keybind.wasPressed

            if (keybind.isPressed && !wasPressed) {
                settings.onPress(engineClient)
            }
            if (isPressed && wasPressed) {
                settings.onHold(engineClient)
            }
            if (!isPressed && wasPressed) {
                settings.onRelease(engineClient)
            }

            keybind.wasPressed = keybind.isPressed
        }
    }

    private fun KeybindSettings.register(): EngineKeybind = registerKeybinding(this)
}

@JvmInline
value class KeybindId(val value: String)

sealed class KeyBindModifier {
    object Control : KeyBindModifier()
}

typealias KeyBindHandler = (EngineClient) -> Unit

data class KeybindSettings(
    val name: String,
    val id: KeybindId,
    val key: Int,
    val isMouse: Boolean = false,
    val modifiers: List<KeyBindModifier> = listOf(),
    val dev: Boolean = false,
    val onPress: KeyBindHandler = {},
    val onHold: KeyBindHandler = {},
    val onRelease: KeyBindHandler = {},
    val requireWorld: Boolean = false,
    val category: String = CommonEngineServerMod.MOD_ID,
)

data class EngineKeybind(
    val settings: KeybindSettings,
    val minecraft: KeyBinding
) {
    var wasPressed = false
    val isPressed get() = minecraft.isPressed
}