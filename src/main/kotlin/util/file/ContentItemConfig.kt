package org.lain.engine.util.file

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.item.*
import org.lain.engine.util.math.Vec3

@Serializable
data class GunConfig(
    val barrel: BarrelConfig = BarrelConfig(1, 0),
    val ammunition: AmmunitionConfig? = null,
    val display: GunDisplay? = null,
    val smoke: List<Float>? = null,
    val rate: Int = 15
) {
    @Serializable
    data class BarrelConfig(val bullets: Int, val initial: Int = 0)
    @Serializable
    data class AmmunitionConfig(val item: ItemId, val display: String? = null)

    fun gunComponent() = Gun(
        barrel.let { Barrel(it.initial, it.bullets) },
        true,
        false,
        ammunition?.item,
        smoke?.let { Vec3(it[0], it[1], it[2]) },
        rate
    )

    fun gunDisplayComponent(): GunDisplay? {
        val ammunitionDisplay = ammunition?.display
        return display?.copy(ammunition=ammunitionDisplay ?: display.ammunition)
    }
}

@Serializable
data class ItemConfig(
    @SerialName("display_name") val displayName: String,
    val material: String = "stick",
    val model: String? = null,
    val stackable: Boolean? = null,
    @SerialName("stack_size") val maxStackSize: Int? = null,
    val hat: Boolean? = null,
    val gun: GunConfig? = null,
    val tooltip: String? = null,
    val sounds: Map<String, SoundEventId>? = null,
    val mass: Float? = null,
    val writable: WritableConfig? = null,
    val assets: Map<String, String>? = null,
)

@Serializable
data class WritableConfig(val pages: Int, val texture: String? = null)

context(ctx: ContentCompileContext)
internal fun compileItems(itemConfigs: Map<String, ItemConfig>, namespace: FileNamespace): List<CompiledNamespace.Item> {
    val namespaceConfig = namespace.config
    return itemConfigs.map { (id, config) ->
        // Экипировка
        val hat = config.hat ?: namespaceConfig.computeInheritable { it.hat } ?: false

        // Ассеты
        var assets = (config.assets ?: mapOf("default" to config.model)) + namespaceConfig.accumulateInheritable { it.assets }
        assets = assets.mapValues { (_, path) ->
            (path ?: namespaceConfig.model)
                .replaceToRelative(namespace)
                .replace("{id}", id)
        }

        // Звуки
        var sounds = (config.sounds ?: emptyMap()) + namespaceConfig.accumulateInheritable { it.sounds }
        sounds = sounds.mapValues { (_, path) -> SoundEventId(path.value.replaceToRelative(namespace)) }

        // Физические хар-ки
        val stackable = config.stackable ?: namespaceConfig.computeInheritable { it.stackable } ?: false
        var maxStackSize = config.maxStackSize ?: namespaceConfig.computeInheritable { it.maxStackSize }
        if (maxStackSize == null && !stackable) maxStackSize = 1
        val mass = config.mass ?: namespaceConfig.computeInheritable { it.mass }

        CompiledNamespace.Item(
            config,
            ItemPrefab(
                ItemInstantiationSettings(
                    ItemId(namespacedId(namespace.id, id)),
                    maxStackSize ?: 16,
                    ItemName(config.displayName),
                    config.gun?.gunComponent(),
                    config.gun?.gunDisplayComponent(),
                    config.tooltip?.let { ItemTooltip(it) },
                    mass?.let { Mass(it) },
                    config.writable?.let { Writable(it.pages, listOf(), it.texture) },
                    hat,
                    ItemAssets(assets),
                    sounds = ItemSounds(sounds)
                )
            )
        )
    }
}