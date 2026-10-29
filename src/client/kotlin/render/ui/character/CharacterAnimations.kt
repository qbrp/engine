package org.lain.engine.client.render.ui.character

import dev.kosmx.playerAnim.api.IPlayable
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry
import org.lain.engine.mc.engineId

object CharacterAnimations {
    val all = ShuffleBag(
        mutableListOf(
            animation("victory"),
            animation("lucky"),
            animation("bah"),
            animation("hands_up"),
            animation("fear_fall"),
            animation("witch"),
            animation("jotaro"),
            animation("frankenstein"),
            animation("bow")
        )
    )

    fun animation(path: String): IPlayable {
        return PlayerAnimationRegistry.getAnimation(engineId(path))!!
    }
}

class ShuffleBag<T>(private val elements: List<T>) {
    private val bag = elements.shuffled().toMutableList()

    fun next(): T {
        return bag.removeLastOrNull() ?: run {
            bag.clear()
            bag.addAll(elements.shuffled())
            next()
        }
    }
}

fun CharacterPreviewPlayer.playAnimation(animation: IPlayable) {
    val stack = PlayerAnimationAccess.getPlayerAnimLayer(this)
    stack.addAnimLayer(1000, animation.playAnimation())
}