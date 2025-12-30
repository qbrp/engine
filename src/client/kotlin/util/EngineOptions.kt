package org.lain.engine.client.util

import org.lain.engine.util.ENGINE_DIR
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import kotlin.reflect.full.memberProperties

private const val CONFIG_FILENAME = "client.properties"
val CONFIG_DIR = ENGINE_DIR
    .resolve(CONFIG_FILENAME)

interface EngineOptions {
    val chatBubbleScale: Float
    val chatBubbleHeight: Float
    val chatBubbleLineWidth: Int
    val chatBubbleLifeTime: Int
    val arcRadius: Float
    val arcThickness: Float
    val arcOffsetX: Float
    val arcOffsetY: Float
    val chatInputShakingForce: Float
    val chatInputShakingThreshold: Float
    val crosshairIndicatorVisible: Boolean
}
