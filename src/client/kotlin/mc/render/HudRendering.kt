package org.lain.engine.client.mc.render

import com.mojang.logging.LogUtils
import it.unimi.dsi.fastutil.booleans.BooleanConsumer
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.*
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.screen.ConfirmLinkScreen
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.tooltip.TooltipType
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.common.CustomClickActionC2SPacket
import net.minecraft.server.command.CommandManager
import net.minecraft.text.ClickEvent
import net.minecraft.text.ClickEvent.*
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import net.minecraft.world.World
import org.lain.engine.client.EngineClient
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.ScreenRenderer
import org.lain.engine.player.InteractionComponent
import org.lain.engine.player.Narration
import org.lain.engine.util.EngineId
import org.lain.engine.util.Injector
import org.lain.engine.util.component.get
import org.lain.engine.util.component.handle
import org.slf4j.Logger
import java.net.URI

fun registerHudRenderEvent(
    client: MinecraftClient,
    engineClient: EngineClient,
    fontRenderer: MinecraftFontRenderer,
    screenRenderer: ScreenRenderer,
    engineUiRenderPipeline: EngineUiRenderPipeline
) {
    var drawableRoot: DrawableRoot? = null
    HudElementRegistry.addLast(
        EngineId("ui")
    ) { context, tickCounter ->
        if (drawableRoot == null) {
            drawableRoot = DrawableRoot(client.window.scaledWidth, client.window.scaledHeight)
            Injector.register(drawableRoot)
        }
        val deltaTick = tickCounter.fixedDeltaTicks
        val painter = MinecraftPainter(
            deltaTick,
            context,
            fontRenderer
        )
        context.matrices.pushMatrix()
        val window = MinecraftClient.window
        val mouse = MinecraftClient.mouse
        screenRenderer.isFirstPerson = !client.gameRenderer.camera.isThirdPerson
        screenRenderer.chatOpen = client.currentScreen is ChatScreen
        val mainPlayer = engineClient.gameSession?.mainPlayer
        if (mainPlayer != null) {
            mainPlayer.handle<Narration> {
                renderNarrations(
                    context,
                    screenRenderer.narrations,
                    this,
                    deltaTick
                )
            }
            renderInteractionProgression(
                context,
                screenRenderer.interactionProgression,
                mainPlayer.get<InteractionComponent>(),
                deltaTick
            )
            screenRenderer.renderScreen(painter)
            val mouseX = mouse.getScaledX(window).toInt()
            val mouseY = mouse.getScaledY(window).toInt()
            drawableRoot.render(context, mouseX, mouseY, deltaTick)
        }
        //renderConsoleHud(context, screenRenderer.consoleRenderState)
        engineUiRenderPipeline.render(
            context,
            deltaTick,
            mouse.getScaledX(window).toFloat(),
            mouse.getScaledY(window).toFloat()
        )
        context.matrices.popMatrix()
    }
}

class DrawableRoot(var width: Int, var height: Int) : AbstractParentElement(), Drawable {
    private val client: MinecraftClient = MinecraftClient
    private val textRenderer: TextRenderer get() = client.textRenderer

    private val children: MutableList<Element> = mutableListOf()
    private val selectables: MutableList<Selectable> = mutableListOf()
    private val drawables: MutableList<Drawable> = mutableListOf()
    private var initialized = false

    init {
        if (!initialized) {
            this.init()
        } else {
            this.refreshWidgetPositions()
        }
        initialized = true

    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        context.createNewRootLayer()
        for (drawable in this.drawables) {
            drawable.render(context, mouseX, mouseY, deltaTicks)
        }
        context.drawDeferredElements()
    }

    private fun <T> addDrawableChild(drawableElement: T): T where T : Element, T : Drawable {
        this.drawables.add(drawableElement)
        this.children.add(drawableElement)
        this.selectables.add(drawableElement as Selectable)
        return drawableElement
    }

    private fun <T : Drawable> addDrawable(drawable: T): T {
        this.drawables.add(drawable)
        return drawable
    }

    private fun <T> addSelectableChild(child: T): T where T : Element, T : Selectable {
        this.children.add(child)
        this.selectables.add(child)
        return child
    }

    private fun remove(child: Element) {
        if (child is Drawable) {
            this.drawables.remove((child as Any) as Drawable)
        }
        if (child is Selectable) {
            this.selectables.remove((child as Any) as Selectable)
        }
        this.children.remove(child)
    }

    private fun clearChildren() {
        this.drawables.clear()
        this.children.clear()
        this.selectables.clear()
    }

    private fun insertText(text: String?, override: Boolean) {
    }

    fun handleTextClick(style: Style): Boolean {
        val clickEvent = style.getClickEvent()
        if (this.client.isShiftPressed()) {
            if (style.getInsertion() != null) {
                this.insertText(style.getInsertion(), false)
            }
        } else if (clickEvent != null) {
            this.handleClickEvent(this.client, clickEvent)
            return true
        }
        return false
    }

    fun resize(width: Int, height: Int) {
        this.width = width
        this.height = height
        this.refreshWidgetPositions()
    }

    private fun handleClickEvent(client: MinecraftClient, clickEvent: ClickEvent) {
        handleClickEvent(clickEvent, client, this)
    }

    private fun clearAndInit() {
        this.clearChildren()
        this.init()
    }

    private fun setWidgetAlpha(alpha: Float) {
        for (element in this.children()) {
            if (element !is ClickableWidget) continue
            val clickableWidget = element
            clickableWidget.setAlpha(alpha)
        }
    }

    override fun children(): MutableList<out Element> {
        return this.children
    }

    private fun init() {
    }

    private fun refreshWidgetPositions() {
        this.clearAndInit()
    }

    private fun isValidCharacterForName(name: String, codepoint: Int, cursorPos: Int): Boolean {
        val i = name.indexOf(58.toChar())
        val j = name.indexOf(47.toChar())
        if (codepoint == 58) {
            return (j == -1 || cursorPos <= j) && i == -1
        }
        if (codepoint == 47) {
            return cursorPos > i
        }
        return codepoint == 95 || codepoint == 45 || codepoint in 97..122 || codepoint in 48..57 || codepoint == 46
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        return true
    }

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()

        fun getTooltipFromItem(client: MinecraftClient, stack: ItemStack): MutableList<Text?>? {
            return stack.getTooltip(
                Item.TooltipContext.create(client.world as World?),
                client.player as PlayerEntity?,
                (if (client.options.advancedItemTooltips) TooltipType.Default.ADVANCED else TooltipType.Default.BASIC) as TooltipType
            )
        }

        fun handleClickEvent(clickEvent: ClickEvent, client: MinecraftClient, root: DrawableRoot) {
            val clientPlayerEntity = client.player ?: error("Player is not available")

            when(clickEvent) {
                is RunCommand -> {
                    val string = runCatching { clickEvent.command }
                        .onFailure { throw MatchException(it.toString(), it) }
                        .getOrThrow()
                    handleRunCommand(clientPlayerEntity, string)
                    return
                }
                is ShowDialog -> clientPlayerEntity.networkHandler.showDialog(clickEvent.dialog(), null)

                is Custom -> {
                    clientPlayerEntity.networkHandler.sendPacket(
                        CustomClickActionC2SPacket(
                            clickEvent.id(),
                            clickEvent.payload()
                        ) as Packet<*>?
                    )
                    client.setScreen(null)
                }
            }
            handleBasicClickEvent(clickEvent, client, root)
        }

        fun handleBasicClickEvent(clickEvent: ClickEvent, client: MinecraftClient, root: DrawableRoot) {
            val bl2 = when (clickEvent) {
                is OpenUrl -> {
                    val uRI = clickEvent.uri()
                    handleOpenUri(client, uRI)
                    false
                }
                is OpenFile -> {
                    Util.getOperatingSystem().open(clickEvent.file())
                    true
                }
                is SuggestCommand -> {
                    val suggestCommand = clickEvent.command()
                    root.insertText(suggestCommand, true)
                    true
                }
                is CopyToClipboard -> {
                    client.keyboard.clipboard = clickEvent.value
                    true
                }
                else -> {
                    LOGGER.error("Don't know how to handle {}", clickEvent as Any)
                    true
                }
            }
        }

        fun handleOpenUri(client: MinecraftClient, uri: URI): Boolean {
            if (!client.options.getChatLinks().getValue()) {
                return false
            }
            if (client.options.getChatLinksPrompt().getValue()) {
                client.setScreen(ConfirmLinkScreen(BooleanConsumer { confirmed: Boolean ->
                    if (confirmed) {
                        Util.getOperatingSystem().open(uri)
                    }
                    client.setScreen(null)
                }, uri.toString(), false))
            } else {
                Util.getOperatingSystem().open(uri)
            }
            return true
        }

        fun handleRunCommand(player: ClientPlayerEntity, command: String) {
            player.networkHandler.runClickEventCommand(CommandManager.stripLeadingSlash(command), null)
        }

        fun renderBackgroundTexture(
            context: DrawContext,
            texture: Identifier?,
            x: Int,
            y: Int,
            u: Float,
            v: Float,
            width: Int,
            height: Int
        ) {
            val i = 32
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, 32, 32)
        }
    }
}