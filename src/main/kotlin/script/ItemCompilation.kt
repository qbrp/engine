package org.lain.engine.script

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.item.*
import org.lain.engine.player.ProgressionAnimationId
import org.lain.engine.script.yaml.namespacedId
import org.lain.engine.util.NamespaceId
import org.lain.engine.util.then
import org.lain.engine.world.SoundEventId

@Serializable
data class WritableConfig(val pages: Int, val texture: String? = null)

@Serializable
data class FlashlightConfig(
    val radius: Float = 8f,
    val distance: Float = 20f,
    val light: Float = 15f
)

fun CompiledItem(
    namespace: NamespaceId,
    itemId: String,
    displayName: String,
    assets: Map<String, String>? = mapOf(),
    progressionAnimations: Map<String, ProgressionAnimationId>? = mapOf(),
    sounds: Map<String, SoundEventId>? = mapOf(),
    stackSize: Int? = null,
    mass: Float? = null,
    tooltip: String? = null,
    writable: WritableConfig? = null,
    flashlight: FlashlightConfig? = null,
    componentsFactory: () -> List<Component> = { listOf() }
): CompiledNamespace.Item {
    val assets = assets ?: mapOf()
    val progressionAnimations = progressionAnimations ?: mapOf()
    val sounds = sounds ?: mapOf()

    val maxCount = stackSize ?: 16
    val nameComponent = ItemName(displayName)
    val assetsComponent = { assets.isNotEmpty() }.then { ItemAssets(assets) }
    val progressionAnimationsComponent = { progressionAnimations.isNotEmpty() }.then { ItemProgressionAnimations(progressionAnimations) }
    val soundsComponent = sounds.let { if (it.isNotEmpty()) ItemSounds(it) else null }
    val tooltipComponent = tooltip?.let { ItemTooltip(it) }
    val massComponent = mass?.let { Mass(it) }
    return CompiledNamespace.Item(
        ItemPrefab(
            ItemId(namespacedId(namespace, itemId)),
            maxCount,
            nameComponent.text,
            assetsComponent,
            progressionAnimationsComponent,
            {
                listOfNotNull(
                    Count(1, maxCount),
                    nameComponent,
                    tooltipComponent,
                    massComponent,
                    assetsComponent,
                    soundsComponent,
                    progressionAnimationsComponent,
                    writable?.let { Writable(it.pages, listOf(), it.texture) },
                    flashlight?.let { Flashlight(false, ConeLightEmitterSettings(it.radius, it.distance, it.light)) },
                ) + componentsFactory()
            }
        )
    )
}