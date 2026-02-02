package org.lain.engine.util

import org.lain.engine.EngineMinecraftServer
import org.lain.engine.item.ItemStorage
import org.lain.engine.mc.EngineItemContext
import org.lain.engine.mc.EntityTable
import org.lain.engine.server.EngineServer
import org.lain.engine.transport.ServerTransportContext
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

object Injector {
    private val map = mutableMapOf<KClass<*>, Any>()

    fun <T: Any> register(clazz: KClass<T>, instance: T) {
        map[clazz] = instance
    }

    inline fun <reified T : Any> register(instance: T) {
        register(T::class, instance)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> resolve(clazz: KClass<T>): T =
        resolveOrNull(clazz) ?: error("No dependency for $clazz")

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> resolveOrNull(clazz: KClass<T>): T? = map[clazz] as? T

    inline fun <reified T : Any> resolve() = resolve(T::class)
}

class Inject<T>(private val provider: () -> T?) {
    operator fun getValue(thisRef: Any?, prop: KProperty<*>): T {
        return provider() ?: error("Dependency ${prop.name} is not initialized")
    }
}

inline fun <reified T : Any> injectValue() = Injector.resolve(T::class)

inline fun <reified T: Any> inject() = Inject { Injector.resolve(T::class) }

enum class Environment {
    CLIENT, SERVER
}

fun injectServerTransportContext() = inject<ServerTransportContext>()

fun injectEngineServer() = inject<EngineServer>()

fun injectMinecraftEngineServer() = inject<EngineMinecraftServer>()

fun injectEntityTable() = inject<EntityTable>()

fun injectItemContext() = inject<EngineItemContext>()

fun injectItemStorage() = inject<ItemStorage>()

fun registerMinecraftServer(
    server: EngineMinecraftServer
) {
    Injector.register<EngineServer>(server.engine)
    Injector.register<EngineMinecraftServer>(server)
}