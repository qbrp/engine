package org.lain.engine.client.mc.render

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.util.CommonColors
import org.lain.engine.mc.MathMc
import org.lain.engine.mc.Text

/* !! VIBE CODING WARNING !! */

class SingleSelectionListWidget<T>(
    private val client: Minecraft,
    var x: Int,
    var y: Int,
    var width: Int,
    var height: Int,
    private val itemHeight: Int = 15
) : ContainerEventHandler, Renderable, NarratableEntry {
    data class Entry<T>(val value: T, val text: Text)

    private val entries = mutableListOf<Entry<T>>()
    private var selectedIndex: Int = -1
    private var scroll: Float = 0f

    /** Колбек: вызывается при клике по элементу (index, value) */
    var onSelect: ((Int, T) -> Unit)? = null

    // --- API для работы со списком ---
    fun add(text: Text, value: T) {
        entries += Entry(value, text)
        if (selectedIndex == -1) selectedIndex = 0
        clampScroll()
    }

    fun clear() {
        entries.clear()
        selectedIndex = -1
        scroll = 0f
    }

    val size: Int get() = entries.size

    fun getSelected(): T? = entries.getOrNull(selectedIndex)?.value

    fun setSelected(index: Int) {
        if (index in 0 until entries.size) {
            selectedIndex = index
            ensureVisible(index)
        }
    }

    fun setSelectedByValue(value: T) {
        val i = entries.indexOfFirst { it.value == value }
        if (i >= 0) setSelected(i)
    }

    fun moveSelection(delta: Int) {
        if (entries.isEmpty()) return
        if (selectedIndex == -1) selectedIndex = 0
        selectedIndex = MathMc.clamp(selectedIndex + delta, 0, entries.lastIndex)
        ensureVisible(selectedIndex)
    }

    // --- рендер ---
    override fun render(ctx: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // фон
        ctx.fill(x, y, x + width, y + height, 0x77000000) // полупрозрачный фон

        if (entries.isEmpty()) return

        // вычисляем видимую область
        val totalHeight = entries.size * itemHeight
        clampScroll()
        val startIndex = (scroll / itemHeight).toInt().coerceAtLeast(0)
        val visibleCount = (height / itemHeight) + 1
        val endIndex = (startIndex + visibleCount).coerceAtMost(entries.size)

        // рисуем элементы
        for (i in startIndex until endIndex) {
            val itemTop = y + (i * itemHeight) - scroll.toInt()
            val itemBottom = itemTop + itemHeight

            // подсветка при наведении
            val hovered = mouseX in x..(x + width) && mouseY in itemTop..(itemBottom - 1)

            // выбранный элемент — яркая полоса
            if (i == selectedIndex) {
                ctx.fill(x, itemTop, x + width, itemBottom, 0xFF6666FF.toInt())
            } else if (hovered) {
                ctx.fill(x, itemTop, x + width, itemBottom, 0x44000000)
            }

            // текст элемента
            val textX = x + 4
            val textY = itemTop + (itemHeight / 2) - (client.font.lineHeight / 2)
            ctx.drawString(client.font, entries[i].text, textX, textY, CommonColors.WHITE, true)
        }

        // простая полоса прокрутки (если нужно)
        if (totalHeight > height) {
            val barWidth = 6
            val scrollRatio = scroll / (totalHeight - height)
            val handleH = (height * (height.toFloat() / totalHeight)).toInt().coerceAtLeast(10)
            val handleY = y + (scrollRatio * (height - handleH)).toInt()
            ctx.fill(x + width - barWidth, y, x + width, y + height, 0x22000000)
            ctx.fill(x + width - barWidth, handleY, x + width, handleY + handleH, 0x88AAAAAA.toInt())
        }
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mouseX = click.x.toInt()
        val mouseY = click.y.toInt()
        if (mouseX !in x..(x + width) || mouseY !in y..(y + height)) return false
        if (entries.isEmpty()) return true
        val localY = mouseY - y + scroll.toInt()
        val idx = localY / itemHeight
        if (idx in entries.indices) {
            selectedIndex = idx
            onSelect?.invoke(idx, entries[idx].value)
        }
        return true
    }

    override fun children(): List<GuiEventListener> = listOf()

    override fun isDragging(): Boolean = false

    override fun setDragging(dragging: Boolean) {}

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        if (mouseX.toInt() !in x..(x + width) || mouseY.toInt() !in y..(y + height)) return false
        scroll -= (verticalAmount * itemHeight * 3).toFloat()
        clampScroll()
        return true
    }

    override fun getFocused(): GuiEventListener { return this }

    override fun setFocused(focused: GuiEventListener?) {}

    override fun setFocused(focused: Boolean) {}

    override fun isFocused(): Boolean = false

    private fun ensureVisible(index: Int) {
        val top = index * itemHeight
        val bottom = top + itemHeight
        if (top < scroll) scroll = top.toFloat()
        if (bottom > scroll + height) scroll = (bottom - height).toFloat()
        clampScroll()
    }

    private fun clampScroll() {
        val total = entries.size * itemHeight
        val max = (total - height).coerceAtLeast(0)
        if (scroll < 0f) scroll = 0f
        if (scroll > max) scroll = max.toFloat()
    }

    override fun narrationPriority(): NarratableEntry.NarrationPriority = NarratableEntry.NarrationPriority.NONE

    override fun updateNarration(narrationElementOutput: NarrationElementOutput) {}
}