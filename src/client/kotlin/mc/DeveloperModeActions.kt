package org.lain.engine.client.mc

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.util.ActionResult
import org.lain.engine.client.EngineClient
import org.lain.engine.client.chat.LiteralSystemEngineChatMessage
import org.lain.engine.client.mc.render.ChunkDecalsStorage
import org.lain.engine.client.mc.render.TransformationsEditorScreen
import org.lain.engine.client.render.CD
import org.lain.engine.client.render.VOICE_WARNING
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.item.*
import org.lain.engine.mc.BULLET_DAMAGE_DECALS_LAYER
import org.lain.engine.mc.engine
import org.lain.engine.player.ArmStatus
import org.lain.engine.util.Timestamp
import org.lain.engine.util.handle
import org.lain.engine.util.math.VEC3_ZERO
import org.lain.engine.util.math.randomInteger
import org.lain.engine.util.math.roundToInt
import org.lain.engine.world.*
import org.lwjgl.glfw.GLFW

private var developerModeKeyPressedTick = 0L

fun registerDeveloperModeDecalsDebug(decalsStorage: ChunkDecalsStorage, engineClient: EngineClient) {
    var debugDecalsVersion = 0
    UseBlockCallback.EVENT.register { entity, world, hand, result ->
        if (world.isClient && engineClient.developerMode && isControlDown()) {
            val pos = result.blockPos
            val decals = List(10) {
                Decal(
                    randomInteger(16),
                    randomInteger(16),
                    0f,
                    DecalContents.Chip(1, 1f)
                )
            }
            decalsStorage.updateTexture(
                BlockDecals(
                    debugDecalsVersion++,
                    mapOf(
                        BULLET_DAMAGE_DECALS_LAYER to DecalsLayer(Direction.entries.associateWith { decals })
                    )
                ),
                ImmutableVoxelPos(pos.engine())
            )
        }
        ActionResult.PASS
    }
}

fun onKeyDeveloperMode(key: Int): Boolean = with(ClientMixinAccess.getEngineClient()) {
    if (isControlDown() && developerMode) {
        if (ticks - developerModeKeyPressedTick > 20) {
            developerModeKeyPressedTick = ticks
            if (key == GLFW.GLFW_KEY_1) {
                audioManager.playSound(
                    SoundPlay(
                        SoundEvent(
                            SoundEventId("debug_pig_scream"),
                            listOf(
                                SoundSource(
                                    SoundId("funny/pig-scream"),
                                    1f,
                                    1f,
                                    1,
                                    16
                                )
                            )
                        ),
                        VEC3_ZERO,
                        EngineSoundCategory.MASTER,
                    )
                )
                applyLittleNotification(
                    LittleNotification(
                        "Проигран звук",
                        "funny/pig-scream.ogg",
                        sprite = CD,
                    ),
                )
            } else if (key == GLFW.GLFW_KEY_2) {
                val player = MinecraftClient.player ?: return@with true
                val mainHandItemStack = player.mainHandStack
                val offHandItemStack = player.offHandStack
                val itemStack = if (mainHandItemStack.isEmpty) offHandItemStack else mainHandItemStack
                if (developerMode && player.activeItem != null && !itemStack.isEmpty) {
                    MinecraftClient.setScreen(TransformationsEditorScreen(itemStack))
                }
            } else if (key == GLFW.GLFW_KEY_3) {
                val gameSession = gameSession ?: return@with true
                val start = Timestamp()
                repeat(100) {
                    gameSession.chatManager.addMessage(
                        LiteralSystemEngineChatMessage(gameSession, roundToInt(Math.random() * 999999).toString())
                    )
                }

                val end = start.timeElapsed()
                audioManager.playUiNotificationSound()
                applyLittleNotification(
                    LittleNotification(
                        "Добавлено 100 случайных сообщений",
                        "Время: ${end} мл.",
                        sprite = VOICE_WARNING,
                    ),
                )
            } else if (key == GLFW.GLFW_KEY_4) {
                acousticDebug = !acousticDebug
            } else if (key == GLFW.GLFW_KEY_5) {
                gameSession?.mainPlayer?.handle<ArmStatus> {
                    extend = !extend
                }
                audioManager.playUiNotificationSound()
            } else {
                return@with false
            }
        }
        return@with true
    }
    return@with false
}