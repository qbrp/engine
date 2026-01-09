package org.lain.engine.client.mc

import com.daqem.yamlconfig.api.config.ConfigExtension
import com.daqem.yamlconfig.api.config.ConfigType
import com.daqem.yamlconfig.api.config.IConfig
import com.daqem.yamlconfig.api.config.entry.IConfigEntry
import com.daqem.yamlconfig.impl.config.ConfigBuilder
import org.lain.engine.CommonEngineServerMod
import org.lain.engine.client.util.EngineOptions
import org.lain.engine.util.EngineId

class EngineYamlConfig(
    builder: ConfigBuilder = ConfigBuilder(
        CommonEngineServerMod.MOD_ID,
        "engine-config",
        ConfigExtension.YAML,
        ConfigType.CLIENT
    )
) : EngineOptions {

    private val chatBubbleScaleProperty = builder.defineFloat("chat.chat-bubble-scale", 0.1f, 0.01f, 3f)
    private val chatBubbleHeightProperty = builder.defineFloat("chat.chat-bubble-height", 2.3f, 1f, 10f)
    private val chatBubbleLineWidthProperty = builder.defineInteger("chat.chat-bubble-line-width", 200, 50, 1000)
    private val chatBubbleLifeTimeProperty = builder.defineInteger("chat.chat-bubble-life-time", 200, 2, 2400)
    private val chatInputShakingForceProperty = builder.defineFloat("chat-input.shaking-force", 3f, 0f, 5f)
    private val chatInputShakingThresholdProperty = builder.defineFloat("chat-input.shaking-threshold", 0.5f, 0f, 1f)

    private val arcRadiusProperty = builder.defineFloat("crosshair.arc-radius", 5f, 0.05f, 10f)
    private val arcThicknessProperty = builder.defineFloat("crosshair.arc-thickness", 5f, 0.05f, 10f)
    private val arcOffsetXProperty = builder.defineFloat("crosshair.arc-offset-x", 0f, -10f, 10f)
    private val arcOffsetYProperty = builder.defineFloat("crosshair.arc-offset-y", 0f, -10f, 10f)
    private val crosshairIndicatorVisibleProperty = builder.defineBoolean("crosshair.indicator-visible", false)

    override val chatBubbleScale: Float
        get() = chatBubbleScaleProperty.get() / 50f
    override val chatBubbleHeight: Float
        get() = chatBubbleHeightProperty.get()
    override val chatBubbleLineWidth: Int
        get() = chatBubbleLineWidthProperty.get()
    override val chatBubbleLifeTime: Int
        get() = chatBubbleLifeTimeProperty.get()
    override val chatInputShakingForce: Float
        get() = chatInputShakingForceProperty.get()
    override val chatInputShakingThreshold: Float
        get() = chatInputShakingThresholdProperty.get()

    override val arcRadius: Float
        get() = arcRadiusProperty.get()
    override val arcThickness: Float
        get() = arcThicknessProperty.get()
    override val arcOffsetX: Float
        get() = arcOffsetXProperty.get()
    override val arcOffsetY: Float
        get() = arcOffsetYProperty.get()
    override val crosshairIndicatorVisible: Boolean
        get() = crosshairIndicatorVisibleProperty.get()

    val config: IConfig = builder.build()
}
