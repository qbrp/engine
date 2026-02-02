package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.util.Component
import org.lain.engine.util.get

@Serializable
data class ItemTooltip(val text: String) : Component

fun EngineItem.getTooltip(): List<String> {
    val lines = mutableListOf<String>()

    get<ItemTooltip>()?.let { tooltip ->
        lines += "<gray>${tooltip.text}</gray>"
    }

    get<Gun>()?.let { gun ->
        val ammunition = get<GunDisplay>()?.ammunition ?: gun.ammunition.value
        val selector = when(gun.selector) {
            true -> "<red>поставлен"
            false -> "<green>снят"
        }
        val charged = when(gun.barrel.bullets > 0) {
            true -> "<green>Заряжен"
            false -> "<red>Разряжен"
        }
        lines += "<aqua>■</aqua> <gray>Боеприпасы $ammunition"
        lines += "<aqua>■</aqua> <gray>Предохранитель <aqua>$selector</aqua>"
        lines += "<aqua>■</aqua> $charged"
    }

    return lines
}