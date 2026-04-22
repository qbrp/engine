package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.transport.packet.ItemComponent
import org.lain.engine.world.World

@Serializable
data class ItemTooltip(val text: String) : ItemComponent

context(world: World)
fun EngineItem.getTooltip(debug: Boolean): List<String> {
    val lines = mutableListOf<String>()

    getComponent<ItemTooltip>()?.let { tooltip ->
        lines += "<gray>${tooltip.text}</gray>"
    }

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

    val (uuid, id) = requireComponent<ItemMeta>()
    if (debug) {
        lines.add("<dark_gray>$id</dark_gray>")
        lines.add("<dark_gray>$uuid</dark_gray>")
    }

    return lines
}