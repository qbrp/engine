package org.lain.engine.client.mc

import me.fzzyhmstrs.fzzy_config.api.ConfigApi
import me.fzzyhmstrs.fzzy_config.api.FileType
import me.fzzyhmstrs.fzzy_config.api.RegisterType
import me.fzzyhmstrs.fzzy_config.config.Config
import me.fzzyhmstrs.fzzy_config.config.ConfigSection
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedFloat
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt
import net.minecraft.network.message.SentMessage
import org.lain.engine.client.util.EngineOptions
import org.lain.engine.util.EngineId

class EngineFzzyConfig : Config(ID), EngineOptions {
    val chat = ChatSection()
    class ChatSection : ConfigSection() {
        val chatBubbleScaleProperty = ValidatedFloat(1f, 3f, 0.5f)
        val chatBubbleHeightProperty = ValidatedFloat(2.3f, 10f, 1f)
        val chatBubbleLineWidthProperty = ValidatedInt(200, 1000, 50)
        val chatBubbleLifeTimeProperty = ValidatedInt(200, 2400, 2)
        val chatInputShakingForceProperty = ValidatedFloat(3f, 5f, 0f)
        val chatInputShakingThresholdProperty = ValidatedFloat(0.5f, 1f, 0f)
    }

    val crosshair = CrosshairSection()
    class CrosshairSection : ConfigSection() {
        val arcRadiusProperty = ValidatedFloat(5f, 10f, 0.05f)
        val arcThicknessProperty = ValidatedFloat(5f, 10f, 0.05f)
        val arcOffsetXProperty = ValidatedFloat(0f, 10f, -10f)
        val arcOffsetYProperty = ValidatedFloat(0f, 10f, -10f)
        val crosshairIndicatorVisible = ValidatedBoolean(false)
    }

    override val chatBubbleScale: Float
        get() = chat.chatBubbleScaleProperty.get()
    override val chatBubbleHeight: Float
        get() = chat.chatBubbleHeightProperty.get()
    override val chatBubbleLineWidth: Int
        get() = chat.chatBubbleLineWidthProperty.get()
    override val chatBubbleLifeTime: Int
        get() = chat.chatBubbleLifeTimeProperty.get()
    override val chatInputShakingForce: Float
        get() = chat.chatInputShakingForceProperty.get()
    override val chatInputShakingThreshold: Float
        get() = chat.chatInputShakingThresholdProperty.get()
    override val arcRadius: Float
        get() = crosshair.arcRadiusProperty.get()
    override val arcThickness: Float
        get() = crosshair.arcThicknessProperty.get()
    override val arcOffsetX: Float
        get() = crosshair.arcOffsetXProperty.get()
    override val arcOffsetY: Float
        get() = crosshair.arcOffsetYProperty.get()
    override val crosshairIndicatorVisible: Boolean
        get() = crosshair.crosshairIndicatorVisible.get()

    companion object {
        val ID = EngineId("config")
    }
}

object FzzyConfigs {
    var CLIENT = ConfigApi.registerAndLoadConfig(::EngineFzzyConfig, RegisterType.CLIENT)
}