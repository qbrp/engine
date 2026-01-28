package test

import org.lain.engine.client.render.FontRenderer
import org.lain.engine.client.render.Vec2
import org.lain.engine.client.render.ui.Background
import org.lain.engine.client.render.ui.ClickListener
import org.lain.engine.client.render.ui.Composition
import org.lain.engine.client.render.ui.CompositionRenderContext
import org.lain.engine.client.render.ui.Fragment
import org.lain.engine.client.render.ui.HorizontalLayout
import org.lain.engine.client.render.ui.InputResult
import org.lain.engine.client.render.ui.Layout
import org.lain.engine.client.render.ui.Size
import org.lain.engine.client.render.ui.Sizing
import org.lain.engine.client.render.ui.State
import org.lain.engine.client.render.ui.TextArea
import org.lain.engine.client.render.ui.UiContext
import org.lain.engine.client.render.ui.UiState
import org.lain.engine.client.render.ui.VerticalLayout
import org.lain.engine.client.render.ui.layout
import org.lain.engine.client.render.ui.mutableStateOf
import org.lain.engine.client.render.ui.recompose
import org.lain.engine.client.render.ui.remember
import org.lain.engine.util.Color
import org.lain.engine.util.text.EngineOrderedText
import org.lain.engine.util.text.EngineText
import org.lain.engine.util.text.TextColor
import kotlin.test.BeforeTest
import kotlin.test.Test

class UiTest {
    private lateinit var context: UiContext

    @BeforeTest
    fun setup() {
        context = UiContext(
            DummyFontRenderer(),
            Size(100f, 100f)
        )
    }

    @Test
    fun testRecompose() {
        /*
        - Root
        -- A (0, 0)
        --- AA (0, 0)
        --- AB (10, 0)
        -- B (0, 20)
         */

        val root1 = {
            Fragment(
                layout = VerticalLayout(0f),
                children = listOf(
                    Fragment(
                        layout = HorizontalLayout(0f),
                        sizing = Sizing(20f, 20f),
                        children = listOf(
                            Fragment(
                                sizing = Sizing(10f, 10f)
                            ),
                            Fragment(
                                sizing = Sizing(10f, 10f)
                            )
                        )
                    ),
                    Fragment(
                        layout = HorizontalLayout(0f),
                        sizing = Sizing(20f, 20f)
                    ),
                    Fragment(
                        layout = HorizontalLayout(0f),
                        sizing = Sizing(20f, 20f)
                    )
                )
            )
        }
        val composition = Composition(root1)
        recompose(composition, context)

        val A = composition.children[0]
        val B = composition.children[1]

        recompose(A, context)

        assert(A.pos == Vec2(0f, 0f))
        assert(A.children[0].pos == Vec2(0f, 0f))
        assert(A.children[1].pos == Vec2(10f, 0f))
        assert(B.pos == Vec2(0f, 20f))
    }

    val Composition.pos get() = this.render.position

    private class DummyFontRenderer(override val fontHeight: Float = 0f) : FontRenderer {
        override fun getWidth(text: EngineOrderedText): Float {
            throw NotImplementedError()
        }

        override fun breakTextByLines(
            text: EngineText,
            width: Float
        ): List<EngineOrderedText> {
            throw NotImplementedError()
        }
    }

    @Test
    fun testRemember() {
        val fragment = { RememberFragment() }
        val composition = Composition(fragment)
        CompositionRenderContext.startRendering(composition, context)
        recompose(composition, context)
        composition.fragment.onRender!!(composition.render)

        CompositionRenderContext.startRendering(composition, context)
        recompose(composition, context)
        composition.fragment.onRender!!(composition.render)
        assert((composition.slots[0] as State<Int>).get() == 2)
    }

    @Test
    fun testFragmentEquality() {
        val clickListener: ClickListener = { _, _, _ -> InputResult.CONTINUE }
        val fragment1 = Fragment(
            position = Vec2(1f, 1f),
            layout = VerticalLayout(2f),
            onClick = clickListener,
            text = TextArea(
                EngineText("123", TextColor.Single(Color.WHITE)),
                1f
            ),
            children = listOf(
                Fragment(
                    position = Vec2(1f, 1f),
                    layout = VerticalLayout(2f),
                    onClick = clickListener,
                    text = TextArea(
                        EngineText("123", TextColor.Single(Color.WHITE)),
                        1f
                    ),
                    background = Background(Color.AQUA)
                )
            ),
            background = Background(Color.AQUA)
        )

        val fragment2 = Fragment(
            position = Vec2(1f, 1f),
            layout = VerticalLayout(2f),
            onClick = clickListener,
            text = TextArea(
                EngineText("123", TextColor.Single(Color.WHITE)),
                1f
            ),
            children = listOf(
                Fragment(
                    position = Vec2(1f, 1f),
                    layout = VerticalLayout(2f),
                    onClick = clickListener,
                    text = TextArea(
                        EngineText("123", TextColor.Single(Color.WHITE)),
                        1f
                    ),
                    background = Background(Color.AQUA)
                )
            ),
            background = Background(Color.AQUA)
        )

        assert(fragment1 == fragment2)
    }

    private fun RememberFragment(): Fragment {
        val incrementState by remember { mutableStateOf(0) }
        return Fragment(
            sizing = Sizing(10f, 10f),
            onRender = { incrementState.set(incrementState.get() + 1) }
        )
    }
}