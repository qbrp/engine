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

    getComponent<Magazine>()?.let { magazine ->
        val fullness = magazine.bullets.toFloat() / magazine.capacity
        lines += "<gray>Предназначен для ${magazine.ammunition}"
        lines += when(fullness) {
            0f -> "<red>Пустой"
            in 0f..0.3f -> "<gold>Почти пустой"
            in 0.3f..0.5f -> "<yellow>Заполнен наполовину"
            in 0.5f..0.7f -> "<dark_green>Заполнено больше половины"
            in 0.7f..0.9f -> "<dark_green>Почти полон"
            else -> "<green>Полный"
        }
    }

    getComponent<Gun>()?.let { gun ->
        val display = getComponent<GunDisplay>()
        val barrel = getComponent<Barrel>()
        val fireState = getComponent<GunFireState>()
        val ammunitionName =
            display?.ammunition?.let { "Боеприпасы $it" }
            ?: display?.magazine?.let { "Принимает $it" }

        if (ammunitionName != null) {
            lines += "<gray>$ammunitionName"
        }

        val showSelector = display?.selectorStatus ?: true
        if (showSelector && fireState != null) {
            val selector = when (fireState.mode) {
                FireMode.SELECTOR -> "<red>предохранитель"
                FireMode.SINGLE -> "<green>одиночный"
                FireMode.AUTO -> "<yellow>автоматический"
            }
            lines += "<gray>Режим огня:</gray> $selector"
        }

        if (barrel != null && barrel.maxBullets > 0) {
            val charged = when(barrel.bullets > 0) {
                true -> "<green>Заряжен"
                false -> "<red>Разряжен"
            }
            lines += charged
        }
    }

    val (uuid, id) = requireComponent<Item>()
    if (debug) {
        lines.add("<dark_gray>$id</dark_gray>")
        lines.add("<dark_gray>$uuid</dark_gray>")
    }

    return lines
}