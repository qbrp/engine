package org.lain.engine.util.component

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.ComponentTypeProvider
import org.lain.cyberia.ecs.KClassComponentTypeProvider
import org.lain.engine.container.*
import org.lain.engine.item.*
import org.lain.engine.player.Outfit
import org.lain.engine.player.PlayerContainer
import org.lain.engine.player.PlayerContainerTag
import org.lain.engine.player.PlayerEquipment
import org.lain.engine.script.EntityRpcReceiver
import org.lain.engine.script.ScriptComponent
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.storage.Savable
import org.lain.engine.storage.SaveTag
import org.lain.engine.storage.UnloadTag
import org.lain.engine.world.*
import kotlin.reflect.KClass

data class ComponentMeta(val savable: Boolean, val serializationClass: KClass<out Any>?, val networking: Boolean)

object CommonComponentTypeProvider : ComponentTypeProvider {
    override fun componentTypeOf(component: Component): ComponentType<out Component> {
        return if (component is ScriptComponent) {
            component.type
        } else {
            ComponentTypeRegistry.componentTypeOf(component::class)
        }
    }
}

object ComponentTypeRegistry : KClassComponentTypeProvider {
    private val types: MutableMap<String, Entry<out Component>> = HashMap()
    private val ids: MutableMap<KClass<out Component>, String> = HashMap() // кеш ID

    data class Entry<T : Component>(val type: ComponentType<T>, val meta: ComponentMeta)

    private fun KClass<out Component>.cachedId(): String {
        return ids.getOrPut(this) { qualifiedName!!.replace(".", "_") }
    }

    fun get(id: String) = types[id]

    inline fun <reified T : Component> registerComponent(
        isSavable: Boolean = false,
        serializationClass: KClass<out Component>? = T::class,
        isNetworking: Boolean = false,
        id: String? = null
    ) {
        registerComponent(T::class, ComponentMeta(isSavable, serializationClass, isNetworking), id)
    }

    fun isRegistered(type: KClass<out Component>): Boolean {
        return types.containsKey(type.cachedId())
    }

    fun registerComponent(kClass: KClass<out Component>, meta: ComponentMeta, id: String? = null) {
        registerComponent(kClass, ComponentType((id ?: kClass.simpleName!!).lowercase()), meta)
    }

    fun registerComponent(kClass: KClass<out Component>, type: ComponentType<out Component>, meta: ComponentMeta) {
        types[kClass.cachedId()] = Entry(type, meta)
    }

    fun listClasses(): List<KClass<out Component>> = ids.keys.toList()

    fun listEntries() = types.entries.toList()

    override fun <T : Component> componentTypeOf(kClass: KClass<T>): ComponentType<T> {
        return (types[kClass.cachedId()] ?: error("Component type ${kClass.qualifiedName} not registered")).type as ComponentType<T>
    }
}

fun ComponentTypeRegistry.registerComponents() {
    registerComponent<VoxelEvent>()
    registerComponent<BulletFire>()
    registerComponent<WorldSoundPlayRequest>()
    registerComponent<Event>()
    registerComponent<OccupiedSlots>(isNetworking = true)
    registerComponent<Slots>(isNetworking = true)
    registerComponent<Entries>()
    registerComponent<PlayerEquipment>()
    registerComponent<Container>()
    registerComponent<ContainerAnchor>()
    registerComponent<AssignItem>(isNetworking = true)
    registerComponent<AssignSlot>()
    registerComponent<DetachItem>()
    registerComponent<ContainerError>()
    registerComponent<PlayerContainerTag>()
    registerComponent<SaveTag>()
    registerComponent<Networked>(isNetworking = true)
    registerComponent<PlayerContainer>()
    registerComponent<UnloadTag>()
    registerComponent<Location>()
    registerComponent<Savable>()

    registerComponent<DynamicVoxel>()
    registerComponent<ChunkedPos>()

    registerComponent<LightSource>(isSavable = true, isNetworking = true)
    registerComponent<Luminance>(isSavable = true, isNetworking = true)

    registerComponent<WorldSoundPlayRequest.Item>(id = "sound_play_item")
    registerComponent<WorldSoundPlayRequest.Positioned>(id = "sound_play_positioned")
    registerComponent<WorldSoundPlayRequest.Positioned>(id = "sound_play_simple")

    registerComponent<HoldsBy>()
    registerComponent<Item>(isSavable = true, isNetworking = true)
    registerComponent<PersistentIdComponent>(isSavable = true, isNetworking = true)
    registerComponent<ContainedIn>(isSavable = true, isNetworking = true, serializationClass = null)
    registerComponent<AssignedSlot>(isSavable = true, isNetworking = true)
    registerComponent<ItemName>(isSavable = true, isNetworking = true)
    registerComponent<ItemTooltip>(isSavable = true, isNetworking = true)
    registerComponent<ItemSounds>(isSavable = true, isNetworking = true)
    registerComponent<Gun>(isSavable = true, isNetworking = true)
    registerComponent<GunDisplay>(isSavable = true, isNetworking = true)
    registerComponent<Count>(isSavable = true, isNetworking = true)
    registerComponent<Mass>(isSavable = true, isNetworking = true)
    registerComponent<Outfit>(isSavable = true, isNetworking = true)
    registerComponent<Flashlight>(isSavable = true, isNetworking = true)
    registerComponent<Writable>(isSavable = true, isNetworking = true)
    registerComponent<ItemAssets>(isSavable = true, isNetworking = true)
    registerComponent<ItemProgressionAnimations>(isSavable = true, isNetworking = true)

    registerComponent<EntityRpcReceiver>(isNetworking = true, isSavable = true, serializationClass = null)
}
