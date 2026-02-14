package org.lain.engine.client.mc

import com.daqem.yamlconfig.api.config.ConfigExtension
import com.daqem.yamlconfig.api.config.ConfigType
import com.daqem.yamlconfig.api.config.IConfig
import com.daqem.yamlconfig.impl.config.ConfigBuilder
import org.lain.engine.CommonEngineServerMod
import org.lain.engine.client.util.EngineOptions

class EngineYamlConfig(
    builder: ConfigBuilder = ConfigBuilder(
        CommonEngineServerMod.MOD_ID,
        "engine-config",
        ConfigExtension.YAML,
        ConfigType.CLIENT
    )
) : EngineOptions {
    init { builder.push("chat") }

    private val chatFieldWidthProperty = builder.defineInteger("width", 340, 0, 600)
    private val chatFieldSizeProperty = builder.defineInteger("size", 300, 0, 5000)

    init { builder.pop() }

    init { builder.push("chat-bubbles") }

    private val chatBubbleScaleProperty = builder.defineFloat("scale", 1f, 0.01f, 3f)
    private val chatBubbleHeightProperty = builder.defineFloat("height", 1.9f, 1f, 10f)
    private val chatBubbleLineWidthProperty = builder.defineInteger("line-width", 200, 50, 1000)
    private val chatBubbleLifeTimeProperty = builder.defineInteger("life-time", 200, 2, 2400)
    private val chatBubbleBackgroundOpacityProperty = builder.defineInteger("background-opacity", 50, 0, 100)
    private val hideChatBubblesWithUiProperty = builder.defineBoolean("hiding", true)
    private val chatBubbleIgnoreLightLevelProperty = builder.defineBoolean("ignore-light-level", true)

    init { builder.pop() }

    init { builder.push("chat-input") }

    private val chatInputShakingForceProperty = builder.defineFloat("shaking-force", 3f, 0f, 5f)
    private val chatInputShakingThresholdProperty = builder.defineFloat("shaking-threshold", 0.5f, 0f, 1f)
    private val chatInputClosingProperty = builder.defineBoolean("closing", false)

    init { builder.pop() }

    init { builder.push("crosshair") }

    private val crosshairIndicatorVisibleProperty = builder.defineBoolean("indicator-visible", false)

    init { builder.pop() }

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
    override val chatInputSendClosesChat: Boolean
        get() = chatInputClosingProperty.get()
    override val chatBubbleBackgroundOpacity: Float
        get() = chatBubbleBackgroundOpacityProperty.get().toFloat() / 100f
    override val hideChatBubblesWithUi: Boolean
        get() = hideChatBubblesWithUiProperty.get()
    override val chatBubbleIgnoreLightLevel: Boolean
        get() = chatBubbleIgnoreLightLevelProperty.get()
    override val chatFieldWidth: Int
        get() = chatFieldWidthProperty.get()
    override val chatFieldSize: Int
        get() = chatFieldSizeProperty.get()

    override val crosshairIndicatorVisible: Boolean
        get() = crosshairIndicatorVisibleProperty.get()

    val config: IConfig = builder.build()
}
