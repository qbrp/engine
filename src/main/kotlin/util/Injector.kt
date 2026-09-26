package org.lain.engine.util

import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.mc.ServerWorldTable
import org.lain.engine.server.EngineServer
import org.lain.engine.transport.ServerTransportContext
import java.util.Collections
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

object Injector {
    var server: EngineMinecraftServer? = null
    private val map = Collections.synchronizedMap<KClass<*>, Any>(mutableMapOf())

    fun <T : Any> unregister(kclass: KClass<T>) {
        map.remove(kclass)
    }

    inline fun <reified T : Any> unregister() {
        unregister<T>(T::class)
    }

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

class InjectCaching<T>(private val provider: () -> T?) {
    private var cached: T? = null
    operator fun getValue(thisRef: Any?, prop: KProperty<*>): T {
        return cached
            ?: provider()
                ?.also { cached = it }
            ?: error("Dependency ${prop.name} is not initialized")
    }
}

fun <T : Any> lazyUntilNotNull(initializer: () -> T?) =
    object : ReadOnlyProperty<Any?, T?> {
        private var value: T? = null

        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>,
        ): T? = value ?: initializer()?.also { value = it }
    }

inline fun <reified T : Any> injectValue() = Injector.resolve(T::class)

inline fun <reified T: Any> inject() = Inject { Injector.resolve(T::class) }

inline fun <reified T: Any> injectCaching() = InjectCaching { Injector.resolve(T::class) }

enum class Environment {
    CLIENT, SERVER
}

fun injectServerTransportContext() = inject<ServerTransportContext>()

fun requireEngineMinecraftServer() = Injector.server ?: error("No server configured")

fun requireEngineMinecraftServerLazy() = lazy { requireEngineMinecraftServer() }

fun registerMinecraftServer(server: EngineMinecraftServer) {
    Injector.server = server
}

fun isClassAvailable(className: String): Boolean {
    return try {
        Class.forName(className)
        true
    } catch (e: ClassNotFoundException) {
        false
    }
}