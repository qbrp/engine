package org.lain.engine.client.render.ui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.LevelLoadingScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.mc.engineId
import kotlin.collections.plusAssign
import kotlin.compareTo
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.times

object MovingWallpapers {
    private val VIGNETTE = engineId("textures/vignette.png")
    private const val WALLPAPER_MOVE_TICKS = 360.0f
    private const val WALLPAPER_TRANSITION_TICKS = 60.0f
    private const val WALLPAPER_OVERSCAN = 48
    private const val WALLPAPER_TRAVEL = 24.0f

    private data class Wallpaper(val id: net.minecraft.resources.Identifier, val width: Int, val height: Int)

    private val WALLPAPERS = listOf(
        Wallpaper(engineId("textures/wallapers/boys.png"), 3840, 2160),
        Wallpaper(engineId("textures/wallapers/boys2.png"), 3840, 2160),
        Wallpaper(engineId("textures/wallapers/cleaner.png"), 3840, 2034),
        Wallpaper(engineId("textures/wallapers/teddy.png"), 3840, 2054),
    )

    private val random = Random(System.currentTimeMillis())
    private var wallpaperMovementProgress = 0.0f
    private var wallpaperMovementDirection = 1.0f
    private var outgoingWallpaperProgress: Float? = null
    private var wallpaperTransitionTicks = 0.0f
    private var currentWallpaperIndex = random.nextInt(WALLPAPERS.size)
    private var incomingWallpaperIndex: Int? = null
    private var animationTicks = 0.0f

    private val width
        get() = MinecraftClient.window.guiScaledWidth
    private val height
        get() = MinecraftClient.window.guiScaledHeight
    private val shouldMove: Boolean
        get() = MinecraftClient.screen !is LevelLoadingScreen

    fun render(guiGraphics: GuiGraphics, delta: Float) {
        if (shouldMove) {
            animationTicks += delta
        }

        renderWallpaper(
            guiGraphics,
            WALLPAPERS[currentWallpaperIndex],
            outgoingWallpaperProgress ?: wallpaperMovementProgress,
            1.0f
        )
        incomingWallpaperIndex?.let { index ->
            val alpha = Mth.clamp(wallpaperTransitionTicks / WALLPAPER_TRANSITION_TICKS, 0.0f, 1.0f)
            renderWallpaper(guiGraphics, WALLPAPERS[index], wallpaperMovementProgress, alpha)
        }
        guiGraphics.fill(0, 0, width, height, 0x66000000)
        guiGraphics.nextStratum()
        guiGraphics.blurBeforeThisStratum()
        guiGraphics.blit(
            VIGNETTE,
            0, 0,
            width, height,
            0f, 1f,
            0f, 1f
        )

        updateWallpaper(delta)
    }

    fun updateWallpaper(delta: Float) {
        wallpaperMovementProgress += delta / WALLPAPER_MOVE_TICKS * wallpaperMovementDirection
        wallpaperMovementProgress = Mth.clamp(wallpaperMovementProgress, 0.0f, 1.0f)

        if (incomingWallpaperIndex != null) {
            wallpaperTransitionTicks += delta
            if (wallpaperTransitionTicks >= WALLPAPER_TRANSITION_TICKS) {
                currentWallpaperIndex = incomingWallpaperIndex!!
                incomingWallpaperIndex = null
                outgoingWallpaperProgress = null
                wallpaperTransitionTicks = 0.0f
            }
            return
        }

        val reachedMovementEdge = wallpaperMovementProgress == 0.0f || wallpaperMovementProgress == 1.0f
        if (reachedMovementEdge && WALLPAPERS.size > 1) {
            incomingWallpaperIndex = pickNextWallpaperIndex()
            outgoingWallpaperProgress = wallpaperMovementProgress
            wallpaperTransitionTicks = 0.0f
            wallpaperMovementDirection *= -1.0f
        }
    }

    fun pickNextWallpaperIndex(): Int {
        var index = currentWallpaperIndex
        while (index == currentWallpaperIndex) {
            index = random.nextInt(WALLPAPERS.size)
        }
        return index
    }

    private fun renderWallpaper(guiGraphics: GuiGraphics, wallpaper: Wallpaper, progress: Float, alpha: Float) {
        val targetWidth = width + WALLPAPER_OVERSCAN * 2
        val targetHeight = height + WALLPAPER_OVERSCAN * 2
        val scale = max(targetWidth.toFloat() / wallpaper.width, targetHeight.toFloat() / wallpaper.height)
        val drawWidth = ceil(wallpaper.width * scale).toInt()
        val drawHeight = ceil(wallpaper.height * scale).toInt()
        val actualTravel = min(
            WALLPAPER_TRAVEL,
            min((drawWidth - width).toFloat(), (drawHeight - height).toFloat())
        )
        val offset = progress * actualTravel
        val x = -((drawWidth - width + actualTravel) / 2.0f).toInt()
        val y = -((drawHeight - height + actualTravel) / 2.0f).toInt()

        guiGraphics.pose().pushMatrix()
        guiGraphics.pose().translate(offset, offset)
        guiGraphics.blit(
            RenderPipelines.GUI_TEXTURED,
            wallpaper.id,
            x,
            y,
            0.0f,
            0.0f,
            drawWidth,
            drawHeight,
            wallpaper.width,
            wallpaper.height,
            wallpaper.width,
            wallpaper.height,
            ARGB.white(alpha)
        )
        guiGraphics.pose().popMatrix()
    }
}