package org.lain.engine.client.render

import org.lain.engine.client.render.ui.*

fun ChatSettings(): Fragment {
    val children = mutableListStateOf<Fragment>()

    return Fragment(
        layout = HorizontalLayout(4f),
        children = children.get()
    )
}

private fun OpenButton(
    childrenState: MutableListState<Fragment>
): Fragment {
    return Fragment(
        image = Image(
            EXCLAMATION,
            SpriteSizing.Stretch
        ),
        onClick = { state, x, y ->
            childrenState.add(Fragment())
            InputResult.FINISH
        }
    )
}