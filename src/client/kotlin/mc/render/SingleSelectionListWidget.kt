package org.lain.engine.client.mc.render

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.ParentElement
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.Widget
import net.minecraft.text.Text
import net.minecraft.util.Colors
import net.minecraft.util.math.MathHelper

/* !! VIBE CODING WARNING !! */

class SingleSelectionListWidget<T>(
    private val client: MinecraftClient,
    var x: Int,
    var y: Int,
    var width: Int,
    var height: Int,
    private val itemHeight: Int = 15
) : ParentElement, Drawable, Selectable {
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
        selectedIndex = MathHelper.clamp(selectedIndex + delta, 0, entries.lastIndex)
        ensureVisible(selectedIndex)
    }

    // --- рендер ---
    override fun render(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
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
            val textY = itemTop + (itemHeight / 2) - (client.textRenderer.fontHeight / 2)
            ctx.drawText(client.textRenderer, entries[i].text, textX, textY, Colors.WHITE, true)
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

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
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

    override fun children(): List<Element?> = listOf()

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

    override fun getFocused(): Element { return this }

    override fun setFocused(focused: Element?) {}

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

    override fun getType(): Selectable.SelectionType = Selectable.SelectionType.NONE

    override fun appendNarrations(builder: NarrationMessageBuilder) {}
}