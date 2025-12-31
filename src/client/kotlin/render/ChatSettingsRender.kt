package org.lain.engine.client.render

import org.lain.engine.client.render.ui.Fragment
import org.lain.engine.client.render.ui.Image
import org.lain.engine.client.render.ui.Layout
import org.lain.engine.client.render.ui.MutableListState
import org.lain.engine.client.render.ui.MutableState
import org.lain.engine.client.render.ui.Sizing
import org.lain.engine.client.render.ui.SpriteSizing
import org.lain.engine.client.render.ui.mutableListStateOf
import org.lain.engine.client.render.ui.mutableStateOf

fun ChatSettings(): Fragment {
    val children = mutableListStateOf<Fragment>()

    return Fragment(
        layout = Layout.Horizontal(4f),
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
            true
        }
    )
}