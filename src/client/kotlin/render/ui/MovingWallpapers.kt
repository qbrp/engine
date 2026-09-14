package org.lain.engine.client.render.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.util.Mth
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.mc.engineId
import org.lain.engine.util.Color
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object MovingWallpapers {
    private val VIGNETTE = engineId("textures/vignette.png")
    private const val WALLPAPER_MOVE_TICKS = 360.0f
    private const val WALLPAPER_TRANSITION_TICKS = 60.0f
    private const val WALLPAPER_OVERSCAN = 48
    private const val WALLPAPER_TRAVEL = 24.0f

    private var textureManager: WallpaperTextureManager? = null
    private var wallpaperMovementProgress = 0.0f
    private var wallpaperMovementDirection = 1.0f
    private var outgoingWallpaperProgress: Float? = null
    private var wallpaperTransitionTicks = 0.0f
    private var transitioning = false

    private val width
        get() = MinecraftClient.window.guiScaledWidth
    private val height
        get() = MinecraftClient.window.guiScaledHeight

    fun loadWallpapers(client: Minecraft) {
        val manager = textureManager ?: WallpaperTextureManager(client).also { textureManager = it }
        resetAnimation()
        manager.reload()
    }

    fun close() {
        textureManager?.close()
        textureManager = null
        resetAnimation()
    }

    fun next() {
        textureManager
        resetAnimation()
    }

    fun render(guiGraphics: GuiGraphics, delta: Float) {
        val manager = textureManager
        manager?.beginFrame()
        manager?.current?.let { wallpaper ->
            renderWallpaper(
                guiGraphics,
                wallpaper,
                outgoingWallpaperProgress ?: wallpaperMovementProgress,
                1.0f,
            )
        }
        if (transitioning) {
            val alpha = Mth.clamp(wallpaperTransitionTicks / WALLPAPER_TRANSITION_TICKS, 0.0f, 1.0f)
            manager?.next?.let { wallpaper ->
                renderWallpaper(guiGraphics, wallpaper, wallpaperMovementProgress, alpha)
            }
        }
        guiGraphics.fill(0, 0, width, height, 0x66000000)
        MinecraftClient.gameRenderer.processBlurEffect(delta)
        MinecraftClient.mainRenderTarget.bindWrite(false)
        guiGraphics.drawTexturedQuad(
            VIGNETTE,
            0f, width.toFloat(),
            0f, height.toFloat(),
            0f, 1f,
            0f, 1f
        )

        updateWallpaper(delta.coerceAtMost(0.333f))
    }

    fun updateWallpaper(delta: Float) {
        wallpaperMovementProgress += delta / WALLPAPER_MOVE_TICKS * wallpaperMovementDirection
        wallpaperMovementProgress = wallpaperMovementProgress.coerceIn(0.0f, 1.0f)

        if (transitioning) {
            wallpaperTransitionTicks += delta
            if (wallpaperTransitionTicks >= WALLPAPER_TRANSITION_TICKS) {
                textureManager?.advance()
                transitioning = false
                outgoingWallpaperProgress = null
                wallpaperTransitionTicks = 0.0f
            }
            return
        }

        val reachedMovementEdge =
            wallpaperMovementProgress == 0.0f || wallpaperMovementProgress == 1.0f
        if (reachedMovementEdge && textureManager?.next != null) {
            transitioning = true
            outgoingWallpaperProgress = wallpaperMovementProgress
            wallpaperTransitionTicks = 0.0f
            wallpaperMovementDirection *= -1.0f
        }
    }

    private fun renderWallpaper(
        guiGraphics: GuiGraphics,
        wallpaper: WallpaperTextureManager.Wallpaper,
        progress: Float,
        alpha: Float,
    ) {
        val targetWidth = width + WALLPAPER_OVERSCAN * 2
        val targetHeight = height + WALLPAPER_OVERSCAN * 2
        val scale =
            max(targetWidth.toFloat() / wallpaper.width, targetHeight.toFloat() / wallpaper.height)
        val drawWidth = ceil(wallpaper.width * scale).toInt()
        val drawHeight = ceil(wallpaper.height * scale).toInt()
        val actualTravel = min(
            WALLPAPER_TRAVEL,
            min((drawWidth - width).toFloat(), (drawHeight - height).toFloat())
        )
        val offset = progress * actualTravel
        val x = -((drawWidth - width + actualTravel) / 2.0f).toInt()
        val y = -((drawHeight - height + actualTravel) / 2.0f).toInt()

        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(offset.toDouble(), offset.toDouble(), 0.0)
        guiGraphics.drawTexturedQuad(
            wallpaper.id,
            x.toFloat(),
            (x + drawWidth).toFloat(),
            y.toFloat(),
            (y + drawHeight).toFloat(),
            0f, 1f,
            0f, 1f,
            Color(ColorMc.color(alpha, 0xFFFFFF))
        )
        guiGraphics.pose().popPose()
    }

    private fun resetAnimation() {
        wallpaperMovementProgress = 0.0f
        wallpaperMovementDirection = 1.0f
        outgoingWallpaperProgress = null
        wallpaperTransitionTicks = 0.0f
        transitioning = false
    }
}
