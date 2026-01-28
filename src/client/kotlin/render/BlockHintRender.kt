package org.lain.engine.client.render

import org.lain.engine.client.render.ui.Background
import org.lain.engine.client.render.ui.ConstraintsSize
import org.lain.engine.client.render.ui.Fragment
import org.lain.engine.client.render.ui.HorizontalLayout
import org.lain.engine.client.render.ui.LineBorders
import org.lain.engine.client.render.ui.Padding
import org.lain.engine.client.render.ui.Pivot
import org.lain.engine.client.render.ui.Sizing
import org.lain.engine.client.render.ui.TextArea
import org.lain.engine.client.render.ui.UiContext
import org.lain.engine.client.render.ui.VerticalLayout
import org.lain.engine.client.render.ui.mutableStateOf
import org.lain.engine.client.render.ui.remember
import org.lain.engine.mc.BlockHint
import org.lain.engine.util.BLACK_TRANSPARENT_BG_COLOR
import org.lain.engine.util.BLOCK_HINT_COLOR
import org.lain.engine.util.text.EngineOrderedText
import org.lain.engine.util.text.EngineText

fun BlockHintContainer(window: Window, editor: Boolean, hint: BlockHint?) = Fragment(
    layout = VerticalLayout(2f),
    sizing = Sizing(
        ConstraintsSize.Fixed(200f),
        ConstraintsSize.Wrap
    ),
    position = Vec2(window.widthDp - 4f, 4f),
    pivot = Pivot.TOP_RIGHT,
    children = listOfNotNull(
        hint?.let { BlockHintMenu(it) },
        TestIncrementText(),
    )
)

fun TestIncrementText(): Fragment {
    val value by remember { mutableStateOf(0) }
    return Fragment(
        text = TextArea(EngineText(value.get().toString())),
        onRender = { value.set(value.get() + 1) }
    )
}

fun BlockHintMenu(hint: BlockHint) = Fragment(
    background = Background(BLACK_TRANSPARENT_BG_COLOR),
    borders = LineBorders(BLOCK_HINT_COLOR, 2f),
    padding = Padding(2f, 2f),
    layout = VerticalLayout(2f),
    children = listOf(
        Fragment(
            text = TextArea(
                EngineText(hint.title, bold = true)
            )
        ),
    ) + hint.texts.mapIndexed { index, text ->
        Fragment(
            layout = HorizontalLayout(2f),
            children = listOf(
                Fragment(
                    background = Background(BLOCK_HINT_COLOR),
                    text = TextArea(
                        EngineText(index.toString(), bold = true)
                    )
                ),
                Fragment(
                    text = TextArea(
                        EngineText(text)
                    ),
                    // FIXME: ЛЮТЕЙШИЙ баг с расчетом размера и ЛЮТЕЙШИЙ паддинговый костыль
                    padding = Padding(0f, 0f, 4f, 0f)
                ),
            )
        )
    }
)