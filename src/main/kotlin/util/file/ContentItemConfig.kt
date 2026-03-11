package org.lain.engine.util.file

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.util.math.Vec3
import org.lain.engine.util.then
import org.lain.engine.world.SoundEventId

@Serializable
data class OutfitConfig(
    val layer: SkinLayerId? = null,
    val part: PlayerPart? = null,
    val parts: List<PlayerPart>? = null,
    val separated: Boolean = false,
    val eyes: Boolean = false
)

@Serializable
data class GunConfig(
    val barrel: BarrelConfig = BarrelConfig(1, 0),
    val ammunition: AmmunitionConfig? = null,
    val display: GunDisplay? = null,
    val smoke: List<Float>? = null,
    val modes: List<FireMode>? = null,
    val rate: Int = 15
) {
    @Serializable
    data class BarrelConfig(val bullets: Int, val initial: Int = 0)
    @Serializable
    data class AmmunitionConfig(val item: ItemId, val display: String? = null)

    fun gunComponent(): Gun {
        val modes = modes?.takeIf { it.isNotEmpty() } ?: listOf(FireMode.SELECTOR, FireMode.SINGLE, FireMode.AUTO)
        return Gun(
            barrel.let { Barrel(it.initial, it.bullets) },
            true,
            ammunition?.item,
            smoke?.let { Vec3(it[0], it[1], it[2]) },
            rate,
            0,
            modes.first(),
            modes
        )
    }

    fun gunDisplayComponent(): GunDisplay? {
        val ammunitionDisplay = ammunition?.display
        return display?.copy(ammunition=ammunitionDisplay ?: display.ammunition)
    }
}

@Serializable
data class FlashlightConfig(
    val radius: Float = 8f,
    val distance: Float = 20f,
    val light: Float = 15f
)

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
    val outfit: OutfitConfig? = null,
    val assets: Map<String, String>? = null,
    @SerialName("progression_animations") val progressionAnimations: Map<String, ProgressionAnimationId>? = null,
    val flashlight: FlashlightConfig? = null,
)

@Serializable
data class WritableConfig(val pages: Int, val texture: String? = null)

context(ctx: ContentCompileContext)
internal fun compileItems(itemConfigs: Map<String, ItemConfig>, namespace: FileNamespace): List<CompiledNamespace.Item> {
    val namespaceConfig = namespace.config
    return itemConfigs.map { (id, config) ->
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

        // Анимации прогрессии
        var progressionAnimations = (config.progressionAnimations ?: emptyMap()) + namespaceConfig.accumulateInheritable { it.progressionAnimations }
        progressionAnimations = progressionAnimations.mapValues { (_, path) -> ProgressionAnimationId(path.value.replaceToRelative(namespace)) }

        // Физические хар-ки
        val stackable = config.stackable ?: namespaceConfig.computeInheritable { it.stackable } ?: false
        var maxStackSize = config.maxStackSize ?: namespaceConfig.computeInheritable { it.maxStackSize }
        if (maxStackSize == null && !stackable) maxStackSize = 1
        val mass = config.mass ?: namespaceConfig.computeInheritable { it.mass }

        // Экипировка
        val outfit = config.outfit?.let { (layer, part, parts, separated, dependsEyeY) ->
            val parts = part?.let { listOf(it) } ?: parts ?: error("Не указана часть тела, покрываемая экипировкой. Доступные варианты: part, parts")
            val display = layer?.let { OutfitDisplay.Texture(it) } ?: OutfitDisplay.Separated.takeIf { separated } ?: error("Не указан способ отображения экипировки")
            Outfit(display, parts, dependsEyeY = dependsEyeY)
        } ?: config.hat?.let { Outfit(OutfitDisplay.Separated, listOf(PlayerPart.HEAD)) }

        val assetsProperty = { assets.isNotEmpty() }.then { ItemAssets(assets) }
        val progressionAnimationsProperty = { progressionAnimations.isNotEmpty() }.then { ItemProgressionAnimations(progressionAnimations) }

        val nameComponent = ItemName(config.displayName)
        val gunDisplayComponent = config.gun?.gunDisplayComponent()
        val tooltipComponent = config.tooltip?.let { ItemTooltip(it) }
        val massComponent = mass?.let { Mass(it) }
        val soundComponent = sounds.let { if (it.isNotEmpty()) ItemSounds(it) else null }

        CompiledNamespace.Item(
            config,
            ItemPrefab(
                ItemId(namespacedId(namespace.id, id)),
                maxStackSize ?: 16,
                nameComponent.text,
                assetsProperty,
                progressionAnimationsProperty,
                {
                    listOfNotNull(
                        nameComponent,
                        config.gun?.gunComponent(),
                        gunDisplayComponent,
                        tooltipComponent,
                        massComponent,
                        config.writable?.let { Writable(it.pages, listOf(), it.texture) },
                        assetsProperty,
                        outfit,
                        config.flashlight?.let { Flashlight(false, ConeLightEmitterSettings(it.radius, it.distance, it.light)) },
                        soundComponent,
                        progressionAnimationsProperty
                    )
                }
            )
        )
    }
}