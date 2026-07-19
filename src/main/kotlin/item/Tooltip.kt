package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.world.World
import kotlin.collections.mutableListOf

@Serializable
data class ItemTooltip(val text: String) : Component {
    fun compile() = CompiledItemTooltip(
        text
            .split("<newline>")
            .map { "<gray>$it</gray>" }
    )
}

data class CompiledItemTooltip(val lines: List<String>) : Component

// TODO: функция мутирует состояние предмета, а это лучше вынести в отдельную систему
context(world: World)
fun EngineItem.getTooltip(debug: Boolean): List<String> {
    val compiledLines = getComponent<CompiledItemTooltip>()?.lines ?: getComponent<ItemTooltip>()
        ?.let {
            val component = it.compile()
            setComponent(component)
            component.lines
        }
    val lines = compiledLines?.toMutableList() ?: mutableListOf()

    getComponent<Gun>()?.let { gun ->
        val display = getComponent<GunDisplay>()
        val ammunition = gun.ammunition
        val ammunitionName = display?.ammunition ?: ammunition?.value

        if (ammunitionName != null) {
            lines += "<aqua>■</aqua> <gray>Боеприпасы $ammunitionName"
        }

        val showSelector = display?.selectorStatus ?: true
        if (showSelector) {
            val selector = when (gun.mode) {
                FireMode.SELECTOR -> "<red>предохранитель"
                FireMode.SINGLE -> "<green>одиночный"
                FireMode.AUTO -> "<yellow>автоматический"
            }
            lines += "<aqua>■</aqua> <gray>Режим огня:</gray> $selector"
        }

        if (ammunition != null && gun.barrel.maxBullets > 0) {
            val charged = when(gun.barrel.bullets > 0) {
                true -> "<green>Заряжен"
                false -> "<red>Разряжен"
            }
            lines += "<aqua>■</aqua> $charged"
        }
    }

    val (uuid, id) = requireComponent<Item>()
    if (debug) {
        lines.add("<dark_gray>$id</dark_gray>")
        lines.add("<dark_gray>$uuid</dark_gray>")
    }

    return lines
}