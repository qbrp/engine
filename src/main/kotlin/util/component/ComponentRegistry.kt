package org.lain.engine.util.component

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.KClassComponentTypeProvider
import org.lain.engine.container.*
import org.lain.engine.item.BulletFire
import org.lain.engine.item.HoldsBy
import org.lain.engine.player.PlayerContainer
import org.lain.engine.player.PlayerContainerTag
import org.lain.engine.player.PlayerEquipment
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.Savable
import org.lain.engine.storage.SaveTag
import org.lain.engine.storage.UnloadTag
import org.lain.engine.world.Event
import org.lain.engine.world.Location
import org.lain.engine.world.VoxelEvent
import org.lain.engine.world.WorldSoundPlayRequest
import kotlin.reflect.KClass

data class ComponentMeta(val savable: Boolean, val networking: Boolean)

object ComponentTypeRegistry : KClassComponentTypeProvider {
    private val types: MutableMap<KClass<out Component>, Entry<out Component>> = mutableMapOf()

    data class Entry<T : Component>(val type: ComponentType<T>, val meta: ComponentMeta)

    inline fun <reified T : Component> registerComponent(
        isSavable: Boolean = false,
        isNetworking: Boolean = false,
    ) {
        registerComponent(T::class, ComponentMeta(isSavable, isNetworking))
    }

    fun registerComponent(kClass: KClass<out Component>, meta: ComponentMeta, id: String = kClass.simpleName!!) {
        types[kClass] = Entry(ComponentType(id), meta)
    }

    fun listEntries() = types.entries.toList()

    override fun <T : Component> componentTypeOf(kClass: KClass<T>): ComponentType<T> {
        return (types[kClass] ?: error("Component type ${kClass.qualifiedName} not registered")).type as ComponentType<T>
    }
}

fun ComponentTypeRegistry.registerComponents() {
    registerComponent<VoxelEvent>()
    registerComponent<BulletFire>()
    registerComponent<WorldSoundPlayRequest>()
    registerComponent<Event>()
    registerComponent<AssignedSlot>(isNetworking = true)
    registerComponent<OccupiedSlots>(isNetworking = true)
    registerComponent<Slots>(isNetworking = true)
    registerComponent<ContainedIn>()
    registerComponent<Entries>()
    registerComponent<PlayerEquipment>()
    registerComponent<HoldsBy>()
    registerComponent<Container>()
    registerComponent<Item>()
    registerComponent<ContainerAnchor>()
    registerComponent<AssignItem>(isNetworking = true)
    registerComponent<AssignSlot>()
    registerComponent<DetachItem>()
    registerComponent<ContainerError>()
    registerComponent<PlayerContainerTag>()
    registerComponent<Broadcast>()
    registerComponent<SaveTag>()
    registerComponent<Networked>()
    registerComponent<PlayerContainer>()
    registerComponent<UnloadTag>()
    registerComponent<Location>()
    registerComponent<PersistentId>(isSavable = true)
    registerComponent<Savable>()
}