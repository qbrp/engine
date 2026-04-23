package org.lain.engine.util.component

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.KClassComponentTypeProvider
import org.lain.engine.container.*
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.storage.*
import org.lain.engine.world.*
import kotlin.reflect.KClass

data class ComponentMeta(val savable: Boolean, val serializationClass: KClass<out Component>, val networking: Boolean)

object ComponentTypeRegistry : KClassComponentTypeProvider {
    private val types: MutableMap<String, Entry<out Component>> = mutableMapOf()
    private val ids: MutableMap<KClass<out Component>, String> = mutableMapOf() // кеш ID

    data class Entry<T : Component>(val type: ComponentType<T>, val meta: ComponentMeta)

    private fun KClass<out Component>.cachedId(): String {
        return ids.getOrPut(this) { qualifiedName!!.replace(".", "_") }
    }

    inline fun <reified T : Component> registerComponent(
        isSavable: Boolean = false,
        serializationClass: KClass<out Component> = T::class,
        isNetworking: Boolean = false,
        id: String? = null,
    ) {
        registerComponent(T::class, ComponentMeta(isSavable, serializationClass, isNetworking))
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
    registerComponent<Player>(id = "engine/player")

    registerComponent<HoldsBy>()
    registerComponent<Item>(isSavable = true, isNetworking = true)
    registerComponent<ItemMeta>(isSavable = true, isNetworking = true)
    registerComponent<PersistentId>(isSavable = true, isNetworking = true)
    registerComponent<ContainedIn>(isSavable = true, isNetworking = true, serializationClass = ContainedInDto::class)
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
}
