package org.lain.engine.util.file

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.entity.EquipmentSlot
import net.minecraft.util.Identifier
import org.lain.engine.item.Barrel
import org.lain.engine.item.Gun
import org.lain.engine.item.GunDisplay
import org.lain.engine.item.ItemId
import org.lain.engine.mc.ItemEquipment
import org.lain.engine.mc.ItemProperties
import org.lain.engine.util.EngineId

@Serializable
data class GunConfig(
    val barrel: BarrelConfig = BarrelConfig(1, 0),
    val ammunition: AmmunitionConfig
) {
    @Serializable
    data class BarrelConfig(val bullets: Int, val initial: Int = 0)
    @Serializable
    data class AmmunitionConfig(val item: ItemId, val display: String? = null)

    fun gunComponent() = Gun(
        barrel.let { Barrel(it.initial, it.bullets) },
        true,
        ammunition.item
    )

    fun gunDisplayComponent() = ammunition.display?.let { GunDisplay(it) }
}

@Serializable
data class ItemConfig(
    @SerialName("display_name") val displayName: String,
    val material: String = "stick",
    val model: String? = null,
    val texture: String? = null,
    @SerialName("asset") val assetType: AssetType = AssetType.FILE,
    val stackable: Boolean? = null,
    @SerialName("stack_size") val maxStackSize: Int = 16,
    val equip: ItemEquipment? = null,
    val hat: Boolean? = null,
    val gun: GunConfig? = null,
    val tooltip: String? = null,
    val sounds: Map<String, String>? = null
) {
    enum class AssetType {
        GENERATED, FILE
    }
}

fun compileItemConfig(id: String, item: ItemConfig, namespace: NamespaceContents, namespaceConfig: NamespaceConfig): ItemProperties {
    val namespacedId = NamespaceItemId(namespace.id, id)
    val material = Identifier.ofVanilla(item.material.lowercase())
    val cfgMaxStackSize = item.maxStackSize
    val cfgStackable = item.stackable ?: namespaceConfig.stackable ?: false
    val stackSize = if (cfgStackable) cfgMaxStackSize else 1
    val asset = when(item.assetType) {
        ItemConfig.AssetType.FILE -> item.model?.replaceToRelative(namespace) ?: namespaceConfig.model
            .replaceToRelative(namespace)
            .replace("{id}", id)
        ItemConfig.AssetType.GENERATED -> item.texture ?: id
    }
    val assetId = EngineId(asset)

    val hat = (item.hat ?: namespaceConfig.hat)?.let { if (it) ItemEquipment(EquipmentSlot.HEAD) else null }
    val equip = (item.equip ?: namespaceConfig.equip) ?: hat

    return ItemProperties(
        namespacedId,
        material,
        assetId,
        stackSize,
        equip
    )
}