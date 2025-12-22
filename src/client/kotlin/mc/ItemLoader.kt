package org.lain.engine.client.mc

import kotlinx.serialization.Serializable
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemGroups
import net.minecraft.item.Items
import net.minecraft.text.Text
import net.minecraft.util.Identifier

data class ClientItemPrefab(
    val id: String,
    val name: Text,
    val material: Identifier,
    val asset: Identifier
)

@Serializable
data class ClientItemGroup(
    val name: String,
    val items: List<String>
)

@Serializable
data class ClientEngineItemGroups(
    val groups: Map<String, ClientItemGroup> = mapOf()
)

fun adaptItemGroups(
    groups: ClientEngineItemGroups
) {
    val minecraftGroups = ItemGroups.getGroups()
}