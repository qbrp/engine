package org.lain.engine.client.mc

import com.daqem.yamlconfig.api.config.IConfig
import com.daqem.yamlconfig.client.gui.screen.ConfigScreen
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lain.engine.client.EngineClient
import org.lain.engine.client.control.*
import org.lain.engine.util.EngineId
import org.lwjgl.glfw.GLFW

fun isControlDown() = InputUtil.isKeyPressed(MinecraftClient.window, GLFW.GLFW_KEY_LEFT_CONTROL)

fun isShiftDown() = InputUtil.isKeyPressed(MinecraftClient.window, GLFW.GLFW_KEY_LEFT_SHIFT)

class KeybindManager(
    private val category: KeyBinding.Category = KeyBinding.Category.create(EngineId("category")),
    private val config: IConfig
) {
    private val keybinds = mutableMapOf<KeybindId, EngineKeybind>()
    val adjustChatVolume = ADJUST_CHAT_VOLUME.register()
    val decreaseChatVolume = DECREASE_CHAT_VOLUME.register()
    val resetChatVolume = RESET_CHAT_VOLUME.register()

    init {
        DEVELOPER_MODE.register()
        HIDE_INTERFACE.register()
        ALLOW_SPEED_INTENTION_CHANGE.register()
        TOGGLE_CHAT_SPY.register()
        EXTEND_HAND.register()

        KeybindSettings(
            name = "Настройки",
            id = KeybindId("options"),
            key = GLFW.GLFW_KEY_F7,
            onPress = { client ->
                MinecraftClient.setScreen(
                    ConfigScreen(null, config)
                )
            }
        ).register()
    }

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
                category,
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
            if (modifiers.contains(KeyBindModifier.Control) && !isControlDown()) continue

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
)

data class EngineKeybind(
    val settings: KeybindSettings,
    val minecraft: KeyBinding
) {
    var wasPressed = false
    val isPressed get() = minecraft.isPressed
}